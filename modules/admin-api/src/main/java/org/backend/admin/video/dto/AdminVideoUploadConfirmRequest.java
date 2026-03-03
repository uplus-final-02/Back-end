package org.backend.admin.video.dto;

public record AdminVideoUploadConfirmRequest(
        Long contentId,
        String title,
        String description,
        String objectKey
) {
}