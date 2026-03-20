package org.backend.userapi.common.scheduler;

import content.repository.TrendingHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.backend.userapi.content.service.TrendingContentService;
import org.backend.userapi.metrics.MetricJobRunWriterService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class TrendingChartScheduler {
    private final TrendingContentService trendingContentService;
    private final MetricJobRunWriterService jobRunWriterService;
    private final TrendingHistoryRepository trendingHistoryRepository;

    @Scheduled(cron = "20 0 * * * *")
    @SchedulerLock(
            name            = "trendingChartTask",
            lockAtMostFor   = "PT55M",  // 크래시 안전망 — 1시간 주기보다 5분 짧게 (다음 사이클 차단 최소화)
            lockAtLeastFor  = "PT5M"    // 최소 5분 유지
    )
    public void buildTrendingChart() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime currentBucket = now.truncatedTo(ChronoUnit.HOURS);

        log.info("[Scheduler] 인기 급상승 차트 갱신 스케줄러 실행 시각: {}", now);
        var run = jobRunWriterService.startTrending(currentBucket);

        long beforeCount = trendingHistoryRepository.countByCalculatedAt(currentBucket);

        try {
            trendingContentService.calculateTrendingScores(currentBucket);

            long afterCount = trendingHistoryRepository.countByCalculatedAt(currentBucket);
            long processed = Math.max(0, afterCount - beforeCount);

            if (processed == 0) {
                jobRunWriterService.empty(run.getId(), "트렌딩 집계 결과 없음(스냅샷 데이터 부족/점수 0)");
            } else {
                jobRunWriterService.success(run.getId(), processed, "트렌딩 이력 저장 완료");
            }

            log.info("[Scheduler] 인기 급상승 차트 갱신 완료. processed={}", processed);

        } catch (Exception e) {
            log.error("[Scheduler] 트렌딩 집계 중 에러 발생: {}", e.getMessage(), e);
            jobRunWriterService.failed(run.getId(), e);
        }
    }
}
