package org.backend.userapi.upload.dto;

public record UserUploadPresignRequest(
        Long userContentId,
        String originalFilename,
        String contentType
) {}