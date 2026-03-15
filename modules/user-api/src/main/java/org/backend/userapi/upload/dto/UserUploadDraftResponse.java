package org.backend.userapi.upload.dto;

public record UserUploadDraftResponse(
        Long userContentId,
        Long userVideoFileId
) {}