package org.backend.userapi.content.dto;

public record UserThumbnailUploadResponse(
        Long userContentId,
        String thumbnailUrl
) {}