package org.backend.userapi.recommendation.dto;

import org.backend.userapi.search.document.ContentDocument;

import java.util.List;

public record RecommendedContentResponse(
        Long    contentId,
        String  title,
        String  contentType,
        String  thumbnailUrl,
        String  accessLevel,
        Long    totalViewCount,
        Long    bookmarkCount,
        List<String> tags
) {
    public static RecommendedContentResponse from(ContentDocument doc) {
        return new RecommendedContentResponse(
                doc.getContentId(),
                doc.getTitle(),
                doc.getContenttype(),
                doc.getThumbnailUrl(),
                doc.getAccessLevel(),
                doc.getTotalViewCount(),
                doc.getBookmarkCount(),
                doc.getTags()
        );
    }
}
