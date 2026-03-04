package org.backend.admin.video.dto;

public record AdminEpisodeDraftResponse(
        Long contentId,
        Long videoId,
        Long videoFileId,
        Integer episodeNo
) {
}