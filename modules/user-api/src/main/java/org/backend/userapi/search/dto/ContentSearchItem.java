package org.backend.userapi.search.dto;

import org.backend.userapi.search.document.ContentDocument;

public record ContentSearchItem(
        Long contentId,
        String title,
        String description,
        String type,
        String status,
        String accessLevel,
        Long totalViewCount,
        Long bookmarkCount
) {
    public static ContentSearchItem from(ContentDocument document) {
        return new ContentSearchItem(
                document.getContentId(),
                document.getTitle(),
                document.getDescription(),
                document.getType(),
                document.getStatus(),
                document.getAccessLevel(),
                document.getTotalViewCount(),
                document.getBookmarkCount()
        );
    }
}
