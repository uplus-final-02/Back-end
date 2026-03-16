package org.backend.userapi.content.dto;

import common.enums.ContentStatus;
import common.enums.VideoStatus;

public record UserContentUpdateResponse(
        Long userContentId,
        ContentStatus contentStatus,
        VideoStatus videoStatus
) {}