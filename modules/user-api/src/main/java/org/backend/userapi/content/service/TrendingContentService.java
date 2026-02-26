package org.backend.userapi.content.service;

import content.dto.TrendingStatDto;
import content.entity.Content;
import content.repository.ContentMetricSnapshotRepository;
import content.repository.ContentRepository;
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
    private final StringRedisTemplate redisTemplate;
    private final UserRepository userRepository;

    private static final String TRENDING_KEY = "content:trending";

    // 가중치 상수
    private static final int WEIGHT_VIEW = 1;
    private static final int WEIGHT_COMPLETED = 3;
    private static final int WEIGHT_BOOKMARK = 5;

    @Transactional(readOnly = true)
    public void calculateTrendingScores(LocalDateTime currentBucketTime) {
        // 1. 집계 범위 설정 (최근 1시간)
        LocalDateTime startTime = currentBucketTime.minusHours(1);
        LocalDateTime endTime = currentBucketTime;

        log.info("[Trending Chart] 점수 산출 및 Redis 적재 시작 (범위: {} ~ {}", startTime, endTime);

        // 1. DB 에서 1시간치 합산 데이터 조회
        List<TrendingStatDto> stats = snapshotRepository.findAggregatedStats(startTime, endTime);

        if (stats.isEmpty()) {
            log.warn("[Trending Chart] 집계할 데이터가 없습니다.");
            return;
        }

        // 2. Redis에 한 번에 넣기 위한 TypedTuple 세트 생성
        Set<ZSetOperations.TypedTuple<String>> tuples = stats.stream()
             .map(stat -> {
                 double score = (stat.getTotalDeltaView() * WEIGHT_VIEW)
                     + (stat.getTotalDeltaCompleted() * WEIGHT_COMPLETED)
                     + (stat.getTotalDeltaBookmark() * WEIGHT_BOOKMARK);

                 return new DefaultTypedTuple<>(String.valueOf(stat.getContentId()), score);
             })
             .filter(tuple -> tuple.getScore() > 0) // 점수가 있는 것만 선별
             .collect(Collectors.toSet());

        if (!tuples.isEmpty()) {
            // 3. 기존 데이터 삭제 후 Bulk Insert 수행
            redisTemplate.delete(TRENDING_KEY);
            redisTemplate.opsForZSet().add(TRENDING_KEY, tuples);

            log.info("[Trending Chart] {}개의 콘텐츠 순위가 Redis에 적재되었습니다.", stats.size());
        }
    }

    @Transactional(readOnly = true)
    public List<TrendingResponse> getTrendingList(int limit) {
        // 1. Redis에서 상위 N개 조회
        Set<ZSetOperations.TypedTuple<String>> typedTuples =
            redisTemplate.opsForZSet().reverseRangeWithScores(TRENDING_KEY, 0, limit - 1);

        if (typedTuples == null || typedTuples.isEmpty()) return List.of();

        List<Long> contentIds = typedTuples.stream()
                                           .map(tuple -> Long.parseLong(tuple.getValue()))
                                           .toList();

        // 2. DB에서 콘텐츠 정보 조회
        List<Content> contents = contentRepository.findAllById(contentIds);
        Map<Long, Content> contentMap = contents.stream()
                                                .collect(Collectors.toMap(Content::getId, content -> content));

        // 3. 업로더 닉네임 일괄 조회 (ContentService 로직 이식)
        Map<Long, String> uploaderNicknameMap = getUploaderNicknameMap(contents);

        // 4. 결과 조립
        List<TrendingResponse> response = new ArrayList<>();
        int rank = 1;

        for (ZSetOperations.TypedTuple<String> tuple : typedTuples) {
            Long contentId = Long.parseLong(tuple.getValue());
            Content content = contentMap.get(contentId);

            if (content != null) {
                // uploaderName 결정 로직
                String uploaderName = (content.getUploaderId() == null)
                    ? "관리자"
                    : uploaderNicknameMap.getOrDefault(content.getUploaderId(), "알 수 없음");

                response.add(TrendingResponse.builder()
                             .rank(rank++)
                             .trendingScore(tuple.getScore())
                             .content(DefaultContentResponse.from(content, uploaderName))
                             .build());
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
