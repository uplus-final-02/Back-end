package org.backend.userapi.content.dto;

import common.enums.ContentStatus;

public record UserContentUpdateRequest(
        String title,
        String description,
        String thumbnailUrl,
        ContentStatus contentStatus
) {}