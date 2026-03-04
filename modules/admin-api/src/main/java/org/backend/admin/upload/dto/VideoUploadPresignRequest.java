package org.backend.admin.upload.dto;

public record VideoUploadPresignRequest(
        Long contentId,
        String originalFilename,
        String contentType
) {
}