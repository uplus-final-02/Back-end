package org.backend.admin.metrics.controller;

import core.security.principal.JwtPrincipal;
import lombok.RequiredArgsConstructor;
import org.backend.admin.metrics.dto.AdminTrendingDetailResponse;
import org.backend.admin.metrics.dto.AdminTrendingTimelineResponse;
import org.backend.admin.metrics.dto.AdminTrendingVerifyResponse;
import org.backend.admin.metrics.service.AdminMetricQueryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

import static org.springframework.format.annotation.DateTimeFormat.ISO;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/metrics/trending")
public class AdminTrendingMetricController {

    private final AdminMetricQueryService queryService;


     // 트렌딩 상세(정각 topN)
    @GetMapping("/detail")
    public AdminTrendingDetailResponse getTrendingDetail(
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestParam @DateTimeFormat(iso = ISO.DATE_TIME) LocalDateTime calculatedAt,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return queryService.getTrendingDetail(calculatedAt, limit);
    }

    // 특정 날짜 타임라인(정각별 topN)
    @GetMapping("/timeline")
    public AdminTrendingTimelineResponse getTimeline(
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestParam String date, // yyyy-MM-dd
            @RequestParam(defaultValue = "20") int limit
    ) {
        return queryService.getTrendingTimeline(date, limit);
    }

    // 반영 검증: snapshots(1h=6버킷 합) vs trending_history delta 비교
    @GetMapping("/verify")
    public AdminTrendingVerifyResponse verify(
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestParam @DateTimeFormat(iso = ISO.DATE_TIME) LocalDateTime calculatedAt
    ) {
        return queryService.verifyTrending(calculatedAt);
    }
}