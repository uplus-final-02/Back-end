package org.backend.userapi.content.dto;

import common.enums.ContentStatus;

public record UserContentUpdateResponse(
        Long userContentId,
        ContentStatus contentStatus,
        String thumbnailUrl
) {}