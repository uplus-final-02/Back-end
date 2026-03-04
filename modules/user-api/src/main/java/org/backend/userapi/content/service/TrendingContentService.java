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
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import user.repository.UserNicknameInfo;
import user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrendingContentService {

    private final ContentRepository contentRepository;
    private final ContentMetricSnapshotRepository snapshotRepository;
    private final UserRepository userRepository;
    private final TrendingHistoryRepository trendingHistoryRepository;

    //private static final String TRENDING_KEY = "content:trending";

    // 가중치 상수
    private static final int WEIGHT_VIEW = 1;
    private static final int WEIGHT_COMPLETED = 3;
    private static final int WEIGHT_BOOKMARK = 5;

    // 저장할 랭킹 개수
    private static final int MAX_TRENDING_SIZE = 20;

    // 1시간 점수 불러오기 + 계산 + DB 에 저장 메서드
    @Transactional
    public void calculateTrendingScores(LocalDateTime currentBucketTime) {
        // 1. 집계 범위 설정 (최근 1시간)
        LocalDateTime startTime = currentBucketTime.minusHours(1);
        LocalDateTime endTime = currentBucketTime;

        log.info("[Trending Chart] 점수 산출 및 Redis 적재 시작 (범위: {} ~ {})", startTime, endTime);

        // 2. DB 에서 1시간치 합산 데이터 조회
        List<TrendingStatDto> stats = snapshotRepository.findAggregatedStats(startTime, endTime);

        if (stats.isEmpty()) {
            log.warn("[Trending Chart] 집계할 데이터가 없습니다.");
            return;
        }

        // 3. 점수 계산, 필터링, 정렬 및 Top N 추출
        List<TrendingStatDto> sortedStats = stats.stream()
                     .filter(stat -> calculateScore(stat) > 0) // 점수가 있는 것만 선별
                     .sorted((a, b) -> Double.compare(calculateScore(b), calculateScore(a))) // 내림차순 정렬
                     .limit(MAX_TRENDING_SIZE) // 상위 20개 제한
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
    }

    // 점수 계산 메서드
    private double calculateScore(TrendingStatDto stat) {
        return (stat.getTotalDeltaView() * WEIGHT_VIEW)
            + (stat.getTotalDeltaCompleted() * WEIGHT_COMPLETED)
            + (stat.getTotalDeltaBookmark() * WEIGHT_BOOKMARK);
    }

    // 저장한 랭킹 불러오기 메서드
    @Transactional(readOnly = true)
    public List<TrendingResponse> getTrendingList(int limit) {
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
            .limit(limit)
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
        List<TrendingResponse> response = new ArrayList<>();

        for (TrendingHistory history : histories) {
            Content content = contentMap.get(history.getContentId());

            // 물리적 FK가 없으므로, 콘텐츠가 삭제되었을 경우를 대비한 방어 로직
            if (content != null) {
                String uploaderName = (content.getUploaderId() == null)
                    ? "관리자"
                    : uploaderNicknameMap.getOrDefault(content.getUploaderId(), "알 수 없음");

                response.add(TrendingResponse.builder()
                                             .rank(history.getRanking()) // DB에 기록된 실제 순위 사용
                                             .trendingScore(history.getTrendingScore())
                                             .content(DefaultContentResponse.from(content, uploaderName))
                                             .build());
            } else {
                log.warn("[Trending Chart] 랭킹에 포함된 콘텐츠(ID: {})가 존재하지 않아 제외되었습니다.", history.getContentId());
            }
        }

        return response;
    }

    /**
     * Content 리스트에서 uploaderId를 추출하여 닉네임 맵 반환 (ContentService 로직 참고)
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
