package org.backend.userapi.content.service;

import content.entity.Content;
import content.entity.ContentMetricSnapshot;
import content.entity.ContentMetricSnapshotId;
import content.repository.ContentRepository;
import content.repository.ContentMetricSnapshotRepository;
import content.repository.WatchHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContentMetricSnapshotService {

    private final ContentRepository contentRepository;
    private final ContentMetricSnapshotRepository snapshotRepository;
    private final WatchHistoryRepository watchHistoryRepository;

    private static final int CHUNK_SIZE = 1000;

    /**
     * 10분 단위 지표 스냅샷 생성
     * @param bucketStartAt 집계 기준 버킷 시각
     */
    @Transactional
    public void createSnapshotsForBucket(LocalDateTime bucketStartAt) {
        LocalDateTime previousBucketStartAt = bucketStartAt.minusMinutes(10);
        LocalDateTime aggregatedAt = LocalDateTime.now();

        // 1. 구간 내 시청 완료 유저 수 사전 집계 (In-memory Map 변환)
        List<Object[]> completedStats = watchHistoryRepository.findCompletedUserCounts(previousBucketStartAt, bucketStartAt);
        Map<Long, Long> completedMap = completedStats.stream()
                                                     .collect(Collectors.toMap(obj -> (Long) obj[0], obj -> (Long) obj[1]));

        int pageNumber = 0;
        int totalProcessedCount = 0;
        boolean hasNextChunk = true;

        log.info("[Snapshot Service] 버킷 시각 {} 기준 스냅샷 집계 시작", bucketStartAt);

        while (hasNextChunk) {
            // 2. 10분 내 수정된 콘텐츠 타겟팅 조회 (Slice 기반 페이징)
            Slice<Content> contentSlice = contentRepository.findByUpdatedAtGreaterThanEqual(
                previousBucketStartAt,
                PageRequest.of(pageNumber, CHUNK_SIZE, Sort.by("id").ascending())
            );

            List<Content> targetContents = contentSlice.getContent();
            if (targetContents.isEmpty()) break;

            List<Long> contentIds = targetContents.stream()
                                                  .map(Content::getId)
                                                  .toList();

            // 3. 직전 스냅샷 데이터 일괄 조회 (Delta 계산용)
            List<ContentMetricSnapshot> previousSnapshots = snapshotRepository.findByIdBucketStartAtAndContentIdIn(
                previousBucketStartAt, contentIds
            );
            Map<Long, ContentMetricSnapshot> previousSnapshotMap = previousSnapshots.stream()
                                                                                    .collect(Collectors.toMap(s -> s.getId().getContentId(), Function.identity()));

            List<ContentMetricSnapshot> newSnapshots = new ArrayList<>(targetContents.size());

            // 4. 콘텐츠별 지표 증분(Delta) 계산
            for (Content content : targetContents) {
                Long contentId = content.getId();

                // 직전 버킷 누적 지표 로드 (미존재 시 0 처리)
                ContentMetricSnapshot prevSnapshot = previousSnapshotMap.get(contentId);
                long prevViewCount = (prevSnapshot != null) ? prevSnapshot.getSnapshotViewCount() : 0L;
                long prevBookmarkCount = (prevSnapshot != null) ? prevSnapshot.getSnapshotBookmarkCount() : 0L;

                // 현재 누적 지표 로드
                long currentViewCount = content.getTotalViewCount();
                long currentBookmarkCount = content.getBookmarkCount();

                // Delta 산출 (음수 방지 보정)
                long deltaView = Math.max(0, currentViewCount - prevViewCount);
                long deltaBookmark = Math.max(0, currentBookmarkCount - prevBookmarkCount);
                long deltaCompleted = completedMap.getOrDefault(contentId, 0L);

                // 5. 무효 데이터 필터링 (지표 변화가 없는 경우 제외)
                if (deltaView == 0 && deltaBookmark == 0 && deltaCompleted == 0) {
                    continue;
                }

                // 6. 스냅샷 엔티티 빌드
                newSnapshots.add(ContentMetricSnapshot.builder()
                                                      .id(new ContentMetricSnapshotId(bucketStartAt, contentId))
                                                      .content(content)
                                                      .snapshotViewCount(currentViewCount)
                                                      .snapshotBookmarkCount(currentBookmarkCount)
                                                      .deltaViewCount(deltaView)
                                                      .deltaBookmarkCount(deltaBookmark)
                                                      .deltaCompletedUserCount(deltaCompleted)
                                                      .aggregatedAt(aggregatedAt)
                                                      .build());
            }

            // 7. 청크 단위 일괄 저장
            if (!newSnapshots.isEmpty()) {
                snapshotRepository.saveAll(newSnapshots);
                totalProcessedCount += newSnapshots.size();
            }

            hasNextChunk = contentSlice.hasNext();
            pageNumber++;
        }

        log.info("[Snapshot Service] 집계 종료 (총 {}건 스냅샷 생성)", totalProcessedCount);
    }
}