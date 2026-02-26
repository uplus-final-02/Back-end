package org.backend.userapi.recommendation.controller;

import core.security.principal.JwtPrincipal;
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
    @GetMapping("/recommended")
    public ResponseEntity<ApiResponse<RecommendationResponse>> getRecommended(
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestParam(defaultValue = "false") boolean extended) {

        RecommendationResponse result =
                recommendationService.recommend(principal.getUserId(), extended);

        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
