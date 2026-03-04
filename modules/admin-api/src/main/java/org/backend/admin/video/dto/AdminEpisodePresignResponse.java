package org.backend.admin.video.dto;

import java.time.Instant;

public record AdminEpisodePresignResponse(
        Long contentId,
        Long videoId,
        String objectKey,
        String putUrl,
        Instant expiresAt
) {
}