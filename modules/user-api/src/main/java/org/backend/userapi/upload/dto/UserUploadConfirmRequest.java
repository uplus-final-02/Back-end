package org.backend.userapi.upload.dto;

public record UserUploadConfirmRequest(
        Long userContentId,
        String objectKey
) {}