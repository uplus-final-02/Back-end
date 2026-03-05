package org.backend.admin.content.dto;

import java.util.List;

import common.enums.ContentAccessLevel;
import common.enums.ContentStatus;

public record AdminContentUpdateRequest(
        String title,
        String description,
        String thumbnailUrl,
        List<Long> tagIds, 
        ContentAccessLevel accessLevel,
        ContentStatus status,
        EpisodeUpdate episode 
) {
    public record EpisodeUpdate(
            Long videoId,         
            String title,
            String description
    ) {}
}