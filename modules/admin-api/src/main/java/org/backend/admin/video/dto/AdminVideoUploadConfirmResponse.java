package org.backend.admin.video.dto;

public record AdminVideoUploadConfirmResponse(
        Long contentId,
        Long videoId,
        Long videoFileId,
        String originalKey,
        String transcodeStatus
) {
}