package org.backend.admin.video.dto;

public record AdminSeriesEpisodeConfirmResponse(
        Long contentId,
        Integer episodeNo,
        Long videoId,
        Long videoFileId,
        String originalKey,
        String transcodeStatus
) {}