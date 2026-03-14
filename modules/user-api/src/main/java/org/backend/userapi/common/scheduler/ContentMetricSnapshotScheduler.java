package org.backend.userapi.common.scheduler;

import content.repository.ContentMetricSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.backend.userapi.content.service.ContentMetricSnapshotService;
import org.backend.userapi.metrics.MetricJobRunWriterService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class ContentMetricSnapshotScheduler {

    private final ContentMetricSnapshotService snapshotService;
    private final MetricJobRunWriterService jobRunWriterService;
    private final ContentMetricSnapshotRepository snapshotRepository;

    /**
     * 10분마다 실행되는 스냅샷 집계 스케줄러
     * 매 정각, 10분, 20분 ... 50분에 정확히 실행
     * (변경) 10초에 실행함
     */
    @Scheduled(cron = "10 0/10 * * * *")
    @SchedulerLock(
            name            = "metricSnapshotTask",
            lockAtMostFor   = "PT15M",  // 크래시 안전망 — 집계 쿼리가 느릴 경우 커버
            lockAtLeastFor  = "PT1M"    // 최소 1분 유지
    )
    public void createMetricSnapshotBucket() {
        LocalDateTime now = LocalDateTime.now();

        // 실행 시각의 '분' 이하를 버리고 10분 단위로 절사하여 버킷 기준 시간(base_time) 생성
        // 예: 14시 10분 2초 실행 -> 14시 10분 버킷
        int minute = now.getMinute();
        int bucketMinute = (minute / 10) * 10;
        LocalDateTime bucketStartAt = now.truncatedTo(ChronoUnit.HOURS)
                                         .plusMinutes(bucketMinute);

        log.info("[Snapshot Scheduler] 10분 단위 지표 스냅샷 집계 시작. (버킷 시각: {})", bucketStartAt);

        var run = jobRunWriterService.startSnapshot(bucketStartAt);

        long beforeCount = snapshotRepository.countByIdBucketStartAt(bucketStartAt);

        try {
            // 비스 로직 호출
            snapshotService.createSnapshotsForBucket(bucketStartAt);

            long afterCount = snapshotRepository.countByIdBucketStartAt(bucketStartAt);
            long processed = snapshotRepository.countByIdBucketStartAt(bucketStartAt);

            if (processed == 0) {
                jobRunWriterService.empty(run.getId(), "집계 대상/변화량 없음");
            } else {
                jobRunWriterService.success(run.getId(), processed, "스냅샷 저장 완료");
            }

            log.info("[Snapshot Scheduler] 스냅샷 집계 완료.");
        } catch (Exception e) {
            log.error("[Snapshot Scheduler] 스냅샷 집계 중 에러 발생: {}", e.getMessage(), e);
            jobRunWriterService.failed(run.getId(), e);
        }
    }
}
