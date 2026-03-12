package org.backend.userapi.common.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.backend.userapi.content.service.TrendingContentService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class TrendingChartScheduler {
    private final TrendingContentService trendingContentService;

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
        trendingContentService.calculateTrendingScores(currentBucket);
    }
}
