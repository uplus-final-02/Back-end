package org.backend.admin.stats.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AdminHomeTagStatsResponse(
        LocalDate statDate,
        Long tagId,
        String tagName,
        Long totalViewCount,
        Long totalBookmarkCount,
        BigDecimal bookmarkRate,
        Long totalWatchCount,
        Long completedWatchCount,
        BigDecimal completionRate
) {
}