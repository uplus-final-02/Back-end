package org.backend.admin.stats.dto;

import java.time.LocalDate;

public record TagHomeStatsBatchResponse(
        LocalDate statDate,
        int savedCount,
        long elapsedMs,
        String message
) {
}
