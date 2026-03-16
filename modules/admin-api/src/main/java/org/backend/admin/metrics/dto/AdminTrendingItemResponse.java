package org.backend.admin.metrics.dto;

import common.enums.ContentType;

public record AdminTrendingItemResponse(
        int rank,
        long contentId,
        String title,
        ContentType type,
        double score,
        long deltaView,
        long deltaBookmark,
        long deltaCompleted,
        Long uploaderId,
        String uploaderName
) {}