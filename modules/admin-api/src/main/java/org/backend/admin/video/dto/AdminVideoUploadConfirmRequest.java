package org.backend.admin.video.dto;

public record AdminVideoUploadConfirmRequest(
        Long contentId,
        Long videoId,
        String objectKey
//        String title,
//        String description
) {
}