package org.backend.admin.content.dto;

import common.enums.ContentAccessLevel;
import common.enums.ContentStatus;

public record AdminContentUpdateResponse (
    Long contentId,
    ContentStatus status,
    ContentAccessLevel accessLevel
) {}
