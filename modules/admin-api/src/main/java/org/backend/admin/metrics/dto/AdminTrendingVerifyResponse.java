package org.backend.admin.metrics.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AdminTrendingVerifyResponse(
        LocalDateTime calculatedAt,
        LocalDateTime windowStart,
        LocalDateTime windowEnd,
        int comparedCount,
        int okCount,
        int diffCount,
        List<Item> diffs
) {
    public record Item(
            long contentId,
            int rank,
            long trendingDeltaView,
            long snapshotDeltaView,
            long trendingDeltaBookmark,
            long snapshotDeltaBookmark,
            long trendingDeltaCompleted,
            long snapshotDeltaCompleted,
            boolean ok
    ) {}
}