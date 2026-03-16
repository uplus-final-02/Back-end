package org.backend.userapi.recommendation.dto;

import org.backend.userapi.search.document.UserContentDocument;

import java.util.List;

/**
 * 유저 업로드 콘텐츠 단건 추천 응답 DTO.
 */
public record UserRecommendedContentResponse(
        Long         userContentId,
        Long         parentContentId,
        String       title,
        String       thumbnailUrl,
        String       accessLevel,
        Long         totalViewCount,
        Long         bookmarkCount,
        List<String> tags
) {
    public static UserRecommendedContentResponse from(UserContentDocument doc) {
        return new UserRecommendedContentResponse(
                doc.getUserContentId(),
                doc.getParentContentId(),
                doc.getTitle(),
                doc.getThumbnailUrl(),
                doc.getAccessLevel(),
                doc.getTotalViewCount(),
                doc.getBookmarkCount(),
                doc.getTags()
        );
    }
}
