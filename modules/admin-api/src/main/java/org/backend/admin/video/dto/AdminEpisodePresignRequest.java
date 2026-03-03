package org.backend.admin.video.dto;

public record AdminEpisodePresignRequest(
        String originalFilename,
        String contentType
) {
}