package org.backend.admin.metrics.controller;

import core.security.principal.JwtPrincipal;
import lombok.RequiredArgsConstructor;
import org.backend.admin.metrics.dto.AdminMetricsDashboardResponse;
import org.backend.admin.metrics.service.AdminMetricsDashboardService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/metrics")
public class AdminMetricsDashboardController {

    private final AdminMetricsDashboardService service;

    @GetMapping("/dashboard")
    public AdminMetricsDashboardResponse dashboard(
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestParam(required = false) LocalDateTime base
    ) {
        return service.getDashboard(base);
    }
}