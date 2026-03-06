package org.backend.admin.content.dto;

import common.enums.ContentAccessLevel;
import common.enums.ContentStatus;
import common.enums.ContentType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record AdminContentDetailResponse(
        Long contentId,
        ContentType type,
        String title,
        Map<String, Object> description,
        String thumbnailUrl,
        ContentStatus status,
        ContentAccessLevel accessLevel,
        Long uploaderId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<TagItem> tags,
        List<EpisodeItem> episodes // SINGLE이면 빈 리스트
) {
    public record TagItem(
            Long tagId,
            String name
    ) {}

    public record EpisodeItem(
            Long videoId,
            Integer episodeNo,
            String title,
            String description
            
    ) {}
}