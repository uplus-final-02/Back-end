package org.backend.admin.stats.scheduler;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.backend.admin.stats.service.TagHomeStatsBatchService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;


@Slf4j
@Component
@RequiredArgsConstructor
public class TagHomeStatsScheduler {

	private final TagHomeStatsBatchService tagHomeStatsBatchService;

    @Scheduled(cron = "0 10 3 * * *")
    public void aggregateDailyHomeTagStats() {
        LocalDate statDate = LocalDate.now().minusDays(1);

        log.info("[Scheduler] 홈 노출 태그 통계 스냅샷 집계 시작: statDate={}", statDate);
        tagHomeStatsBatchService.saveDailyStats(statDate);
    }
}
