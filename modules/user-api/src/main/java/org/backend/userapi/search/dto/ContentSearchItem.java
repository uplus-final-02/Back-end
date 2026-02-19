package org.backend.userapi.search.dto;

import org.backend.userapi.search.document.ContentDocument;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;

public record ContentSearchItem(
        Long contentId,
        String title,            
        String highlightTitle,   
        String highlightDescription, 
        String thumbnailUrl,
        String matchType,        
        List<String> tags
) {
    public static ContentSearchItem from(ContentDocument doc, String keyword) {
        return new ContentSearchItem(
                doc.getContentId(),
                doc.getTitle(),
                doc.getHighlightTitle(),
                doc.getHighlightDescription(),
                doc.getThumbnailUrl(),
                determineMatchType(doc, keyword), 
                doc.getTags()
        );
    }

    private static String determineMatchType(ContentDocument doc, String keyword) {
        if (StringUtils.hasText(doc.getHighlightTitle())) {
            return "TITLE";
        }

        if (StringUtils.hasText(doc.getHighlightDescription())) {
            return "DESCRIPTION";
        }

        if (doc.getTags() != null && StringUtils.hasText(keyword)) {
            String[] tokens = keyword.toLowerCase().trim().split("\\s+"); 
            
            boolean tagMatch = doc.getTags().stream()
                    .filter(StringUtils::hasText)
                    .map(String::toLowerCase)
                    .anyMatch(tag -> Arrays.stream(tokens).anyMatch(tag::contains)); 
            if (tagMatch) return "TAG";
        }

        return "UNKNOWN"; 
    }
}