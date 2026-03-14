package org.backend.admin.stats.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.backend.admin.stats.dto.TagHomeStatsBatchResponse;
import org.backend.admin.stats.service.TagHomeStatsBatchService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/stats/home-tags")
public class TagHomeStatsAdminTestController {

    private final TagHomeStatsBatchService tagHomeStatsBatchService;

    /**
     * 홈 노출 태그 통계 배치를 수동 실행한다.
     *
     * 예)
     * POST /admin/stats/home-tags/run?statDate=2026-03-15
     *
     * statDate 미지정 시 전날 기준으로 실행
     */
    @PostMapping("/run")
    public TagHomeStatsBatchResponse runBatch(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate statDate
    ) {
        LocalDate targetDate = (statDate != null) ? statDate : LocalDate.now().minusDays(1);

        long start = System.currentTimeMillis();

        log.info("[TagHomeStatsTest] 수동 배치 실행 시작: statDate={}", targetDate);

        int savedCount = tagHomeStatsBatchService.saveDailyStats(targetDate);

        long elapsedMs = System.currentTimeMillis() - start;

        log.info("[TagHomeStatsTest] 수동 배치 실행 완료: statDate={}, savedCount={}, elapsedMs={}",
                targetDate, savedCount, elapsedMs);

        return new TagHomeStatsBatchResponse(
                targetDate,
                savedCount,
                elapsedMs,
                "홈 노출 태그 통계 배치 실행 완료"
        );
    }
}
