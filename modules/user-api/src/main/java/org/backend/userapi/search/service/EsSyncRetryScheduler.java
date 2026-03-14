package org.backend.userapi.search.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EsSyncRetryScheduler {

    private final EsSyncFailureService esSyncFailureService;

    // 10분마다 재시도 — 메인 동기화(30초)보다 훨씬 느린 주기로
    @Scheduled(fixedDelayString = "${app.search.dlq-retry.interval-ms:600000}")
    @SchedulerLock(
            name = "esSyncRetryTask",
            lockAtMostFor = "PT9M",
            lockAtLeastFor = "PT1M"
    )
    public void retryFailedSyncs() {
        log.debug("[DLQ Retry] 재시도 스케줄러 실행");
        esSyncFailureService.retryAll();
    }
}