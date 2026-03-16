package org.backend.userapi.content.dto;

import common.enums.ContentStatus;
import common.enums.VideoStatus;

public record UserContentUpdateRequest(
        String title,
        String description,
        ContentStatus contentStatus,
        VideoStatus videoStatus
) {}