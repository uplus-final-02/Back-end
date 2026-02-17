package org.backend.userapi.search.dto;

import org.backend.userapi.search.document.ContentDocument;
import java.util.List;

public record ContentSearchItem(
        Long contentId,
        String title,
        String thumbnailUrl,
        String matchType, // TITLE, TAG, DESCRIPTION
        List<String> tags
) {
    public static ContentSearchItem from(ContentDocument doc, String keyword) {
        return new ContentSearchItem(
                doc.getContentId(),
                doc.getTitle(),
                doc.getThumbnailUrl(),
                determineMatchType(doc, keyword), // 판별 로직 실행
                doc.getTags()
        );
    }

    private static String determineMatchType(ContentDocument doc, String keyword) {
        if (keyword == null || keyword.isBlank()) return "NONE";
        String k = keyword.toLowerCase().trim();
        
        // 1순위: 제목 포함
        if (doc.getTitle().toLowerCase().contains(k)) return "TITLE";
        // 2순위: 태그 포함
        if (doc.getTags() != null && doc.getTags().stream().anyMatch(t -> t.toLowerCase().contains(k))) return "TAG";
        // 3순위: 설명 포함
        return "DESCRIPTION";
    }
}