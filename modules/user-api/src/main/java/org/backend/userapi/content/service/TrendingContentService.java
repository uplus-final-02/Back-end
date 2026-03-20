package org.backend.userapi.content.service;

import content.dto.TrendingStatDto;
import content.entity.Content;
import content.entity.TrendingHistory;
import content.repository.ContentMetricSnapshotRepository;
import content.repository.ContentRepository;
import content.repository.TrendingHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.backend.userapi.content.dto.DefaultContentResponse;
import org.backend.userapi.content.dto.TrendingResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import common.enums.ContentStatus;
import user.repository.UserNicknameInfo;
import user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrendingContentService {

    private final ContentRepository contentRepository;
    private final ContentMetricSnapshotRepository snapshotRepository;
    private final UserRepository userRepository;
    private final TrendingHistoryRepository trendingHistoryRepository;

    // 가중치 상수
    private static final int WEIGHT_VIEW = 1;
    private static final int WEIGHT_COMPLETED = 3;
    private static final int WEIGHT_BOOKMARK = 5;

    // 저장할 랭킹 개수
    private static final int MAX_TRENDING_SIZE = 20;

    // thread-safe 로컬 캐시 (초기값 : 빈 리스트)
    private final AtomicReference<List<TrendingResponse>> trendingCache = new AtomicReference<>(List.of());

    /**
     * 최근 1시간의 지표를 합산하여 트렌딩 점수를 산출하고
     * 상위 N개의 결과를 DB 이력에 적재한 후 캐시를 갱신합니다.
     * * @param currentBucketTime 논리적 기준 시각 (예: 정각)
     */
    @Transactional
    public void calculateTrendingScores(LocalDateTime currentBucketTime) {
        // 1. 집계 범위 설정 (최근 1시간)
        LocalDateTime startTime = currentBucketTime.minusHours(1);
        LocalDateTime endTime = currentBucketTime;

        log.info("[Trending Chart] 점수 산출 및 DB 이력 적재 시작 (범위: {} ~ {})", startTime, endTime);

        // 2. DB 에서 1시간치 합산 데이터 조회
        List<TrendingStatDto> stats = snapshotRepository.findAggregatedStats(startTime, endTime);

        if (stats.isEmpty()) {
            log.warn("[Trending Chart] 집계할 데이터가 없습니다.");
            return;
        }
        
        // 2-1. 집계 대상 content 상태 일괄 조회
        Set<Long> activeContentIds = contentRepository.findAllById(
                stats.stream()
                     .map(TrendingStatDto::getContentId)
                     .collect(Collectors.toSet())
            ).stream()
             .filter(c -> c.getStatus() == ContentStatus.ACTIVE)
             .map(Content::getId)
             .collect(Collectors.toSet());
        
        // 3. 점수 계산, 필터링, 정렬 및 Top N 추출
        List<TrendingStatDto> sortedStats = stats.stream()
        			 .filter(stat -> activeContentIds.contains(stat.getContentId()))
                     .filter(stat -> calculateScore(stat) > 0) // 점수가 있는 것만 선별
                     .sorted((a, b) -> Double.compare(calculateScore(b), calculateScore(a))) // 내림차순 정렬
                     .limit(MAX_TRENDING_SIZE) // 상위 개수 제한
                     .toList();

        if (sortedStats.isEmpty()) {
            log.info("[Trending Chart] 유효한 점수를 획득한 콘텐츠가 없습니다.");
            return;
        }

        // 4. 엔티티 변환 및 순위 부여
        List<TrendingHistory> histories = new ArrayList<>();
        int ranking = 1;

        for (TrendingStatDto stat : sortedStats) {
            histories.add(TrendingHistory.builder()
                 .contentId(stat.getContentId())
                 .ranking(ranking++)
                 .trendingScore(calculateScore(stat))
                 .deltaViewCount(stat.getTotalDeltaView())
                 .deltaBookmarkCount(stat.getTotalDeltaBookmark())
                 .deltaCompletedCount(stat.getTotalDeltaCompleted())
                 .calculatedAt(currentBucketTime) // 논리적 기준 시각
                 .build());
        }

        // 5. DB 일괄 저장
        trendingHistoryRepository.saveAll(histories);
        log.info("[Trending Chart] 상위 {}개의 콘텐츠 순위가 DB 이력에 적재되었습니다.", histories.size());

        // 6. 캐시 갱신 (DB 적재 완료 직후 실행)
        refreshCache();
    }

    /**
     * 트렌딩 가중치 점수 계산
     */
    private double calculateScore(TrendingStatDto stat) {
        return (stat.getTotalDeltaView() * WEIGHT_VIEW)
            + (stat.getTotalDeltaCompleted() * WEIGHT_COMPLETED)
            + (stat.getTotalDeltaBookmark() * WEIGHT_BOOKMARK);
    }

    /**
     * 클라이언트 요청 시 로컬 캐시에서 트렌딩 리스트를 반환합니다.
     * * @param limit 반환할 최대 랭킹 개수
     * @return 캐시된 트렌딩 응답 리스트
     */
    @Transactional(readOnly = true)
    public List<TrendingResponse> getTrendingList(int limit) {
        List<TrendingResponse> cachedList = trendingCache.get();

        // 캐시가 비어있는 경우, 최초 1회 DB load (서버 재시작 직후 등)
        if (cachedList.isEmpty()) {
            cachedList = refreshCache();
        }

        if (cachedList.isEmpty()) {
            return List.of();
        }

        // 요청된 limit 크기에 맞게, 일부 랭킹만 반환
        return (limit >= cachedList.size()) ? cachedList : cachedList.subList(0, limit);
    }

    /**
     * DB에서 최신 트렌딩 이력을 조회하여 로컬 캐시를 원자적으로 갱신하고 반환합니다.
     */
    public List<TrendingResponse> refreshCache() {
        // 1. 가장 최근에 산출된 트렌딩 기준 시각 조회
        Optional<LocalDateTime> latestTimeOpt = trendingHistoryRepository.findLatestCalculatedAt();

        if (latestTimeOpt.isEmpty()) {
            return List.of(); // 아직 트렌딩 데이터가 한 번도 생성되지 않은 경우
        }

        LocalDateTime latestTime = latestTimeOpt.get();

        // 2. 해당 시각의 트렌딩 이력 조회 (랭킹 오름차순) 및 limit 제한
        List<TrendingHistory> histories = trendingHistoryRepository
            .findAllByCalculatedAtOrderByRankingAsc(latestTime)
            .stream()
            .limit(MAX_TRENDING_SIZE)
            .toList();

        if (histories.isEmpty()) {
            return List.of();
        }

        // 3. 연관된 콘텐츠 데이터 일괄 조회
        List<Long> contentIds = histories.stream()
                                         .map(TrendingHistory::getContentId)
                                         .toList();

        List<Content> contents = contentRepository.findAllById(contentIds);
        Map<Long, Content> contentMap = contents.stream()
                                                .collect(Collectors.toMap(Content::getId, content -> content));

        // 4. 업로더 닉네임 일괄 조회
        Map<Long, String> uploaderNicknameMap = getUploaderNicknameMap(contents);

        // 5. 응답 결과 조립 (histories의 정렬된 순서 유지)
        List<TrendingResponse> newList = new ArrayList<>();
        int actualRank = 1; // 동적 순위 부여

        for (TrendingHistory history : histories) {
            Content content = contentMap.get(history.getContentId());

            // 물리적 FK가 없으므로, 콘텐츠가 삭제되었을 경우를 대비한 방어 로직
            if (content == null) {
                log.warn("[Trending Chart] 캐시 갱신 중: 랭킹에 포함된 콘텐츠(ID: {})가 존재하지 않아 제외되었습니다.",
                        history.getContentId());
                continue;
            }

            // 삭제, 숨김 처리된 콘텐츠는 조회 응답에서 제외
            if (content.getStatus() != ContentStatus.ACTIVE) {
                log.info("[Trending Chart] 캐시 갱신 중: 비활성/삭제 콘텐츠(ID: {})가 제외되었습니다.",
                        history.getContentId());
                continue;
            }

            String uploaderName = (content.getUploaderId() == null)
                ? "관리자"
                : uploaderNicknameMap.getOrDefault(content.getUploaderId(), "알 수 없음");

            newList.add(TrendingResponse.builder()
                             .rank(actualRank++) // 조회 시점 기준으로 순위 재부여
                             .trendingScore(history.getTrendingScore())
                             .content(DefaultContentResponse.from(content, uploaderName))
                             .build());
        }

        // 원자적 캐시 덮어쓰기
        trendingCache.set(newList);
        log.info("[Trending Chart] 로컬 캐시 갱신 완료 ({}건)", newList.size());

        return newList;
    }

    /**
     * Content 리스트에서 uploaderId를 추출하여 닉네임 맵 반환 (ContentService 에서 코드 가져옴)
     */
    private Map<Long, String> getUploaderNicknameMap(List<Content> contents) {
        Set<Long> uploaderIds = contents.stream()
                                        .map(Content::getUploaderId)
                                        .filter(Objects::nonNull)
                                        .collect(Collectors.toSet());

        if (uploaderIds.isEmpty()) return Collections.emptyMap();

        List<UserNicknameInfo> results = userRepository.findNicknamesByIds(uploaderIds);
        return results.stream()
                      .collect(Collectors.toMap(UserNicknameInfo::getId, UserNicknameInfo::getNickname));
    }
}
