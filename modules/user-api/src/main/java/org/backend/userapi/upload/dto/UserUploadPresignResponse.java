package org.backend.userapi.upload.dto;

import java.time.Instant;

public record UserUploadPresignResponse(
        Long userContentId,
        String objectKey,
        String putUrl,
        Instant expiresAt
) {}