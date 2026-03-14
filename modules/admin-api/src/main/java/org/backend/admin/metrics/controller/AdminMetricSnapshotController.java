// modules/admin-api/src/main/java/org/backend/admin/metrics/controller/AdminMetricSnapshotController.java
package org.backend.admin.metrics.controller;

import core.security.principal.JwtPrincipal;
import lombok.RequiredArgsConstructor;
import org.backend.admin.metrics.dto.AdminSnapshotBucketSummaryResponse;
import org.backend.admin.metrics.service.AdminMetricQueryService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.format.annotation.DateTimeFormat.ISO;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/metrics/snapshots")
public class AdminMetricSnapshotController {

    private final AdminMetricQueryService queryService;

    /**
     * 버킷 리스트
     * 예) /admin/metrics/snapshots/buckets?from=2026-03-14T00:00:00&to=2026-03-15T00:00:00&page=0&size=50
     */
    @GetMapping("/buckets")
    public List<AdminSnapshotBucketSummaryResponse> getBuckets(
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestParam @DateTimeFormat(iso = ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return queryService.getSnapshotBucketSummaries(from, to, pageable);
    }
}