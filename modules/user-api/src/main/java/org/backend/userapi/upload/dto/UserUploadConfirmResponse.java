package org.backend.userapi.upload.dto;

public record UserUploadConfirmResponse(
        Long userContentId,
        Long userVideoFileId,
        String originalKey,
        String transcodeStatus
) {}