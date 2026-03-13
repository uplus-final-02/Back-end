package org.backend.userapi.video.controller;

import core.security.principal.JwtPrincipal;
import core.storage.service.CloudFrontCookieService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.backend.userapi.common.dto.ApiResponse;
import org.backend.userapi.video.dto.VideoPlayDto;
import org.backend.userapi.video.dto.VideoSimpleMetaData;
import org.backend.userapi.video.service.VideoService;
import org.backend.userapi.video.service.ViewCountService;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import software.amazon.awssdk.services.cloudfront.cookie.CookiesForCustomPolicy;

@RestController
@RequestMapping("/api/contents")
@RequiredArgsConstructor
public class PlayController {

    private final VideoService videoService;
    private final ViewCountService viewCountService;
    private final CloudFrontCookieService cloudFrontCookieService; // 🌟 이거 꼭 추가!

    @GetMapping("/{videoId}/play")
    public ApiResponse<VideoPlayDto> playVideo(
        @PathVariable Long videoId,
        @AuthenticationPrincipal JwtPrincipal jwtPrincipal,
        HttpServletResponse response // 🌟 1. 쿠키를 담기 위해 HTTP 응답 객체 추가
    ) throws Exception {
        VideoPlayDto result = videoService.getPlayInfo(videoId, jwtPrincipal);

        if (result.getUrl() != null && result.getUrl().contains("/hls/")) {
            String[] parts = result.getUrl().split("/"); // ["https:", "", "...", "hls", "461", "master.m3u8"]
            String fileId = parts[4];

            String resourcePath = "hls/" + fileId + "/*";

            CookiesForCustomPolicy cookies = cloudFrontCookieService.generateSignedCookies(resourcePath);

            addCookieToResponse(response, "CloudFront-Policy", cookies.policyHeaderValue());
            addCookieToResponse(response, "CloudFront-Signature", cookies.signatureHeaderValue());
            addCookieToResponse(response, "CloudFront-Key-Pair-Id", cookies.keyPairIdHeaderValue());
        }

        return new ApiResponse<>(200, result.getStatusDescription(), result);
    }

    @PostMapping("/{videoId}/views")
    public ApiResponse<Void> increaseViewCount(
            @PathVariable Long videoId,
            @AuthenticationPrincipal JwtPrincipal jwtPrincipal
    ) {
        VideoSimpleMetaData metaData = videoService.getContentIdByVideoId(videoId);
        viewCountService.incrementViewCount(
                metaData.getContentId(), videoId, jwtPrincipal.getUserId(), metaData.getDurationSec()
        );
        return new ApiResponse<>(200, "조회수 증가 처리 접수", null);
    }

    private void addCookieToResponse(HttpServletResponse response, String name, String value) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                                              .httpOnly(true)       // JS 탈취 방지
                                              .secure(true)         // HTTPS 전용
                                              .path("/")            // 도메인 전체 적용
                                              .sameSite("None")     // 프론트-백엔드 도메인 다를 때 필수
                                              .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }
}