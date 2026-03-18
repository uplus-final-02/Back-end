package org.backend.userapi.recommendation.controller;

import core.security.principal.JwtPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.backend.userapi.common.dto.ApiResponse;
import org.backend.userapi.recommendation.dto.RecommendationResponse;
import org.backend.userapi.recommendation.service.RecommendationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "정식 콘텐츠 추천 API", description = "유저 선호 태그 기반 HNSW kNN 2단계 개인화 추천")
@RestController
@RequestMapping("/api/contents")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    /**
     * 개인화 콘텐츠 추천 (HNSW + 랭킹 + 2-tier 노출)
     *
     * <pre>
     * [기본 노출]
     *   GET /api/contents/recommended
     *   → 상위 15개 반환, hasMore: true
     *
     * [더 알아보기]
     *   GET /api/contents/recommended?extended=true
     *   → 상위 50개 반환, hasMore: false
     * </pre>
     *
     * Header: Authorization: Bearer {accessToken}
     */
    @Operation(summary = "정식 콘텐츠 개인화 추천", description = "유저 선호 태그 기반 HNSW kNN으로 후보를 추출하고, 유사도(60%)·인기도(25%)·신선도(15%)로 최종 랭킹합니다.\n- 기본(extended=false): 상위 15개, hasMore=true\n- 확장(extended=true): 상위 50개")
    @GetMapping("/recommended")
    public ResponseEntity<ApiResponse<RecommendationResponse>> getRecommended(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtPrincipal principal,
            @Parameter(description = "true면 상위 50개 반환 ('더 보기' 용도)") @RequestParam(defaultValue = "false") boolean extended) {

        RecommendationResponse result =
                recommendationService.recommend(principal.getUserId(), extended);

        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
