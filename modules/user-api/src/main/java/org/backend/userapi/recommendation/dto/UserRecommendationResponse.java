package org.backend.userapi.recommendation.dto;

import java.util.List;

/**
 * 유저 업로드 콘텐츠 2-tier 추천 응답.
 *
 * <pre>
 *   [기본 노출]  GET /api/user-contents/recommended
 *     → items 15개, hasMore: true
 *
 *   [더 알아보기] GET /api/user-contents/recommended?extended=true
 *     → items 50개, hasMore: false
 * </pre>
 *
 * @param items   현재 노출할 유저 콘텐츠 목록
 * @param hasMore 더 알아보기 버튼 노출 여부 (extended=true 시 false)
 */
public record UserRecommendationResponse(
        List<UserRecommendedContentResponse> items,
        boolean hasMore
) {}
