package org.backend.userapi.recommendation.controller;

import core.security.principal.JwtPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.backend.userapi.common.dto.ApiResponse;
import org.backend.userapi.recommendation.dto.UserFeedResponse;
import org.backend.userapi.recommendation.dto.UserRecommendationResponse;
import org.backend.userapi.recommendation.service.UserRecommendationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Tag(name = "유저 콘텐츠 추천·피드 API", description = "숏폼 개인화 추천 및 YouTube Shorts 방식 무한스크롤 피드")
@RestController
@RequestMapping("/api/user-contents")
@RequiredArgsConstructor
public class UserRecommendationController {

    private final UserRecommendationService userRecommendationService;

    /**
     * 유저 업로드 콘텐츠 개인화 추천 (HNSW + 랭킹 + 2-tier 노출)
     *
     * <pre>
     * [기본 노출]
     *   GET /api/user-contents/recommended
     *   → 상위 15개 반환, hasMore: true
     *
     * [더 알아보기]
     *   GET /api/user-contents/recommended?extended=true
     *   → 상위 50개 반환, hasMore: false
     * </pre>
     *
     * Header: Authorization: Bearer {accessToken}
     */
    @Operation(summary = "유저 콘텐츠 개인화 추천", description = "부모 콘텐츠 태그를 상속한 벡터로 kNN 추천합니다. extended=false: 상위 15개 / true: 상위 50개")
    @GetMapping("/recommended")
    public ResponseEntity<ApiResponse<UserRecommendationResponse>> getRecommended(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtPrincipal principal,
            @Parameter(description = "true면 상위 50개 반환 ('더 보기' 용도)") @RequestParam(defaultValue = "false") boolean extended) {

        UserRecommendationResponse result =
                userRecommendationService.recommend(principal.getUserId(), extended);

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 유저 업로드 콘텐츠 Shorts 스타일 무한스크롤 피드.
     *
     * <pre>
     * [첫 진입]
     *   GET /api/user-contents/feed
     *   → 선호 태그 기반 초기 10개 + nextSeedId 반환
     *
     * [다음 스크롤]
     *   GET /api/user-contents/feed?seedId={nextSeedId}&excludeIds=1,2,3,4
     *   → 마지막으로 본 콘텐츠와 유사한 다음 10개
     * </pre>
     *
     * @param seedId     마지막으로 본 userContentId (첫 진입 시 생략)
     * @param size       한 번에 반환할 개수 (기본 10, 최대 30)
     * @param excludeIds 이미 본 콘텐츠 ID 콤마 구분 문자열 (예: "1,2,3")
     */
    @Operation(summary = "숏폼 피드 (Shorts 방식)", description = "마지막으로 본 콘텐츠의 tagVector로 다음 콘텐츠를 kNN 조회합니다. 응답의 nextSeedId를 다음 요청 seedId로 사용하세요.")
    @GetMapping("/feed")
    public ResponseEntity<ApiResponse<UserFeedResponse>> getFeed(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtPrincipal principal,
            @Parameter(description = "마지막으로 본 userContentId (첫 진입 시 생략)") @RequestParam(required = false) Long seedId,
            @Parameter(description = "한 번에 받을 개수 (기본 10, 최대 30)") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "이미 받은 콘텐츠 ID 목록 (콤마 구분, 예: 1,2,3)") @RequestParam(required = false) String excludeIds) {

        // size 상한 30으로 제한
        int clampedSize = Math.min(size, 30);

        // excludeIds 파싱 ("1,2,3" → List<Long>)
        List<Long> excludeIdList = StringUtils.hasText(excludeIds)
                ? Arrays.stream(excludeIds.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(Long::parseLong)
                        .collect(Collectors.toList())
                : List.of();

        UserFeedResponse result = userRecommendationService.feed(
                principal.getUserId(), seedId, clampedSize, excludeIdList);

        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
