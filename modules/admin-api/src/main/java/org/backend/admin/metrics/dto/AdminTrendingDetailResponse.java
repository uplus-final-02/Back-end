package org.backend.admin.metrics.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AdminTrendingDetailResponse(
        LocalDateTime calculatedAt,
        int size,
        List<AdminTrendingItemResponse> items
) {}