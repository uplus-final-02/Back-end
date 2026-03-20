package org.backend.admin.upload.dto;

import java.time.Instant;

public record VideoUploadPresignResponse(
        Long contentId,
        String objectKey,
        String putUrl,
        Instant expiresAt
) {
}