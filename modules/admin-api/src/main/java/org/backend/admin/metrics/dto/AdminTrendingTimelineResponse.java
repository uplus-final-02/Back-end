package org.backend.admin.metrics.dto;

import java.time.LocalDateTime;
import java.util.List;

// 특정 날짜의 '정각별 topN' 타임라인
public record AdminTrendingTimelineResponse(
        String date,
        int perHourLimit,
        List<HourBlock> hours
) {
    public record HourBlock(
            LocalDateTime calculatedAt,
            List<AdminTrendingItemResponse> items
    ) {}
}