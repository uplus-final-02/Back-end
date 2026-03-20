package org.backend.admin.video.dto;

public record AdminVideoDraftCreateResponse(
        Long contentId,
        Long videoId,
        Long videoFileId
) {}