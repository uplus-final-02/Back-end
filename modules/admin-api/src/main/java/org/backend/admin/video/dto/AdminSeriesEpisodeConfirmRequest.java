package org.backend.admin.video.dto;

public record AdminSeriesEpisodeConfirmRequest(
        Long videoId,
        String episodeTitle,
        String episodeDescription,
        String objectKey
) {}