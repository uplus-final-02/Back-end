package org.backend.admin.content.dto;

import java.util.List;
import java.util.Map;

import common.enums.ContentAccessLevel;
import common.enums.ContentStatus;

public record AdminContentUpdateRequest(
        String title,
        Map<String, Object> description,
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