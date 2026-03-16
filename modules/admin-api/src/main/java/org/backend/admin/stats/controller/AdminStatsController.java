package org.backend.admin.stats.controller;

import lombok.RequiredArgsConstructor;
import org.backend.admin.common.dto.AdminApiResponse;
import org.backend.admin.stats.dto.AdminHomeTagStatsResponse;
import org.backend.admin.stats.service.AdminStatsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/stats")
public class AdminStatsController {

    private final AdminStatsService adminStatsService;

    @GetMapping("/home-tags")
    public AdminApiResponse<List<AdminHomeTagStatsResponse>> getHomeTagStats(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate statDate
    ) {
        List<AdminHomeTagStatsResponse> result = adminStatsService.getHomeTagStats(statDate);
        return AdminApiResponse.ok("홈 노출 태그 통계 조회 성공", result);
    }
}