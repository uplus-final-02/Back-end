package org.backend.userapi.search.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    // 💡 JSON 파싱을 위한 ObjectMapper (static으로 선언해 재사용)
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static ContentSearchItem from(ContentDocument doc, String keyword) {
        return new ContentSearchItem(
                doc.getContentId(),
                doc.getTitle(),
                doc.getHighlightTitle(),
                // 💡 원본 통 JSON 문자열 대신, 파싱된 줄거리(summary)만 넣기!
                extractSummaryFromJson(doc.getHighlightDescription()), 
                doc.getThumbnailUrl(),
                determineMatchType(doc, keyword), 
                doc.getTags()
        );
    }

    // 💡 JSON 문자열에서 "summary" 필드만 안전하게 추출하는 헬퍼 메서드
    private static String extractSummaryFromJson(String jsonString) {
        if (!StringUtils.hasText(jsonString)) {
            return null;
        }
        try {
            JsonNode rootNode = objectMapper.readTree(jsonString);
            if (rootNode.has("summary")) {
                return rootNode.get("summary").asText();
            }
        } catch (JsonProcessingException e) {
            // 만약 JSON 형식이 아니라 일반 텍스트라면 그대로 반환
            return jsonString;
        }
        return jsonString; // 파싱 실패 시 원본 반환 방어 로직
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