package org.backend.userapi.common.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.backend.userapi.content.service.ContentMetricSnapshotService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class ContentMetricSnapshotScheduler {

    private final ContentMetricSnapshotService snapshotService;

    /**
     * 10분마다 실행되는 스냅샷 집계 스케줄러
     * 매 정각, 10분, 20분 ... 50분에 정확히 실행
     * (변경) 10초에 실행함
     */
    @Scheduled(cron = "10 0/10 * * * *")
    public void createMetricSnapshotBucket() {
        LocalDateTime now = LocalDateTime.now();

        // 실행 시각의 '분' 이하를 버리고 10분 단위로 절사하여 버킷 기준 시간(base_time) 생성
        // 예: 14시 10분 2초 실행 -> 14시 10분 버킷
        int minute = now.getMinute();
        int bucketMinute = (minute / 10) * 10;
        LocalDateTime bucketStartAt = now.truncatedTo(ChronoUnit.HOURS)
                                         .plusMinutes(bucketMinute);

        log.info("[Snapshot Scheduler] 10분 단위 지표 스냅샷 집계 시작. (버킷 시각: {})", bucketStartAt);

        try {
            // 비스 로직 호출
            snapshotService.createSnapshotsForBucket(bucketStartAt);
            log.info("[Snapshot Scheduler] 스냅샷 집계 완료.");
        } catch (Exception e) {
            log.error("[Snapshot Scheduler] 스냅샷 집계 중 에러 발생: {}", e.getMessage(), e);
        }
    }
}
