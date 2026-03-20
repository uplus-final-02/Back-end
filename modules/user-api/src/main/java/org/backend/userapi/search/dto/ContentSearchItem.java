package org.backend.userapi.search.dto;

import org.backend.userapi.search.document.ContentDocument;
import org.springframework.util.StringUtils;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Arrays;
import java.util.List;

@Schema(description = "개별 콘텐츠 검색 상세 정보")
public record ContentSearchItem(
        
        @Schema(description = "콘텐츠 고유 ID", example = "18")
        Long contentId,
        
        @Schema(description = "콘텐츠 제목", example = "무빙")
        String title,            
        
        @Schema(description = "검색어 하이라이트 처리된 제목", example = "<em>무빙</em>", nullable = true)
        String highlightTitle,   
        
        @Schema(description = "검색어 하이라이트 처리된 설명(줄거리)", example = "<em>무빙</em>의 핵심 줄거리입니다...", nullable = true)
        String highlightDescription, 
        
        @Schema(description = "썸네일 이미지 URL", example = "/image/moving_poster.jpg", nullable = true)
        String thumbnailUrl,
        
        @Schema(description = "매칭 타입 (TITLE, DESCRIPTION, TAG, UNKNOWN)", example = "TITLE")
        String matchType,        
        
        @Schema(description = "콘텐츠 장르/태그 목록", example = "[\"초능력\", \"액션\", \"스릴러\"]", nullable = true)
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