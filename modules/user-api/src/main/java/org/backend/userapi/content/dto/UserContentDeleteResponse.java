package org.backend.userapi.content.dto;

import common.enums.ContentStatus;

public record UserContentDeleteResponse(
        Long userContentId,
        ContentStatus contentStatus
) {
    public static UserContentDeleteResponse of(Long id, ContentStatus status) {
        return new UserContentDeleteResponse(id, status);
    }
}