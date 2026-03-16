package org.backend.userapi.recommendation.dto;

import java.util.List;

/**
 * 유저 업로드 콘텐츠 Shorts 스타일 무한스크롤 피드 응답.
 *
 * <pre>
 * [첫 진입]
 *   GET /api/user-contents/feed
 *   → 선호 태그 벡터 기반 초기 10개, nextSeedId 반환
 *
 * [다음 스크롤]
 *   GET /api/user-contents/feed?seedId={nextSeedId}&excludeIds=1,2,3,...
 *   → 마지막으로 본 콘텐츠(seedId)와 유사한 다음 10개
 * </pre>
 *
 * @param items       현재 반환된 유저 콘텐츠 목록
 * @param nextSeedId  다음 요청의 seedId (마지막 항목 ID, 더 이상 없으면 null)
 * @param hasMore     추가 콘텐츠 존재 여부
 */
public record UserFeedResponse(
        List<UserRecommendedContentResponse> items,
        Long    nextSeedId,
        boolean hasMore
) {}
