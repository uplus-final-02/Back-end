package org.backend.admin.content.dto;

import common.enums.ContentStatus;
import common.enums.ContentType;

import java.util.List;

public record AdminContentListResponse(
        Long contentId,
        String title,
        ContentType type,
        Long uploaderId,
        ContentStatus status
) {}