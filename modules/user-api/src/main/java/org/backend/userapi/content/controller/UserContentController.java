package org.backend.userapi.content.controller;

import core.security.principal.JwtPrincipal;
import core.storage.service.CloudFrontCookieService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.backend.userapi.common.dto.ApiResponse;
import org.backend.userapi.content.dto.UserContentDeleteResponse;
import org.backend.userapi.content.dto.UserContentUpdateRequest;
import org.backend.userapi.content.dto.UserContentUpdateResponse;
import org.backend.userapi.content.dto.UserThumbnailUploadResponse;
import org.backend.userapi.content.service.UserContentService;
import org.backend.userapi.content.service.UserWatchHistoryService;
import org.backend.userapi.video.dto.VideoPlayDto;
import org.backend.userapi.video.service.ViewCountService;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.services.cloudfront.cookie.CookiesForCustomPolicy;

@Tag(name = "유저 콘텐츠 API", description = "유저 업로드 숏폼 콘텐츠 재생, 메타데이터 수정, 삭제, 썸네일 업로드, 조회수 증가")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/user/contents")
public class UserContentController {

    private final UserContentService userContentService;
    private final UserWatchHistoryService userWatchHistoryService;
    private final ViewCountService viewCountService;
    private final CloudFrontCookieService cloudFrontCookieService;

    @Operation(summary = "유저 콘텐츠 메타데이터 수정", description = "본인이 업로드한 숏폼의 제목, 설명 등 메타데이터를 수정합니다.")
    @PutMapping("/{userContentId}/metadata")
    public ApiResponse<UserContentUpdateResponse> updateMetadata(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long userContentId,
            @RequestBody UserContentUpdateRequest request
    ) {
        return ApiResponse.ok("유저 콘텐츠 메타데이터 수정 성공",
                userContentService.updateMetadata(principal, userContentId, request));
    }

    @Operation(summary = "유저 콘텐츠 삭제", description = "본인이 업로드한 숏폼을 삭제합니다.")
    @DeleteMapping("/{userContentId}")
    public ApiResponse<UserContentDeleteResponse> delete(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long userContentId
    ) {
        return ApiResponse.ok("유저 콘텐츠 삭제 성공",
                userContentService.delete(principal, userContentId));
    }

    @Operation(summary = "유저 콘텐츠 썸네일 업로드", description = "숏폼의 커스텀 썸네일 이미지를 업로드합니다. 없으면 부모 콘텐츠 썸네일이 사용됩니다.")
    @PostMapping(value = "/{userContentId}/thumbnail",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<UserThumbnailUploadResponse> uploadThumbnail(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long userContentId,
            @RequestPart("file") MultipartFile file
    ) {
        return ApiResponse.ok("썸네일 업로드 성공",
                userContentService.uploadThumbnail(principal, userContentId, file));
    }

    /**
     * 유저 업로드 콘텐츠 시청 기록 저장.
     *
     * <p>숏츠 피드에서 영상 진입 시 호출한다.
     * 이미 시청 이력이 있으면 {@code lastWatchedAt}만 갱신(upsert).
     * 추천 알고리즘 시청 패널티(-0.30) 적용을 위해 필수.
     *
     * POST /api/user/contents/{userContentId}/watch
     */
//    @PostMapping("/{userContentId}/watch")
//    public ApiResponse<Void> recordWatch(
//            @AuthenticationPrincipal JwtPrincipal principal,
//            @PathVariable Long userContentId
//    ) {
//        if (principal == null || principal.getUserId() == null) {
//            throw new IllegalArgumentException("LOGIN_REQUIRED");
//        }
//        userWatchHistoryService.upsertWatchHistory(principal.getUserId(), userContentId);
//        return ApiResponse.ok("시청 기록 저장 성공", null);
//    }

    @Operation(summary = "유저 콘텐츠 조회수 증가", description = "숏폼 재생 시 호출합니다. 20초 쿨타임으로 어뷰징을 방지하며 Redis 버퍼에 기록 후 배치 처리합니다.")
    @PostMapping("/{userContentId}/views")
    public ApiResponse<Void> increaseUserContentView(
        @PathVariable Long userContentId,
        @AuthenticationPrincipal JwtPrincipal jwtPrincipal
    ) {
        // 유저 콘텐츠는 DB 조회 없이 일괄적으로 '20초'의 어뷰징 방지 쿨타임을 적용!
        int FIXED_COOLDOWN_SEC = 20;

        // Redis에 조회수 1 증가
        viewCountService.incrementUserContentViewCount(userContentId, jwtPrincipal.getUserId(), FIXED_COOLDOWN_SEC);

        return new ApiResponse<>(200, "조회수 증가 처리 접수", null);
    }

    /**
     * 🌟 유저 콘텐츠 재생 API (HLS URL 및 CloudFront 서명 쿠키 발급)
     *  시청이력 저장 로직도 포함됨
     */
    @Operation(summary = "유저 콘텐츠 재생 URL 발급", description = "HLS URL과 CloudFront 서명 쿠키를 발급합니다. 재생 전 반드시 호출하세요. videoStatus=PUBLIC + transcodeStatus=DONE 상태일 때만 발급됩니다.")
    @GetMapping("/{userContentId}/play")
    public ApiResponse<VideoPlayDto> playUserContent(
        @PathVariable Long userContentId,
        @AuthenticationPrincipal JwtPrincipal jwtPrincipal
    ) {
        VideoPlayDto result = userContentService.getPlayInfo(userContentId, jwtPrincipal);

        // URL에 "cloudfront.net"이 있을 때만 쿠키 생성
        if (result.getUrl() != null && result.getUrl().contains("cloudfront.net") && result.getUrl().contains("/hls-user/")) {
            try {
                // ⚠️ 주의: 유저 콘텐츠의 S3/CloudFront URL 경로 구조에 맞게 수정 필요!
                // 예: https://domain/hls-user/123/master.m3u8 일 경우 parsing 로직
                String[] parts = result.getUrl().split("/");

                // 기존 방식처럼 fileId(폴더명) 추출. 유저 콘텐츠 URL 구조에 따라 parts 인덱스가 다를 수 있음!
                String fileId = parts[4];
                String resourcePath = "hls-user/" + fileId + "/*";

                CookiesForCustomPolicy cookies = cloudFrontCookieService.generateSignedCookies(resourcePath);

                result.setPolicy(cookies.policyHeaderValue().replace("CloudFront-Policy=", ""));
                result.setSignature(cookies.signatureHeaderValue().replace("CloudFront-Signature=", ""));
                result.setKeyPairId(cookies.keyPairIdHeaderValue().replace("CloudFront-Key-Pair-Id=", ""));
            } catch (Exception e) {
                System.out.println("⚠️ CloudFront 서명 쿠키 생성 실패 (로컬 환경이거나 키 누락): " + e.getMessage());
            }
        }

        return new ApiResponse<>(200, "유저 콘텐츠 재생 정보 조회 성공", result);
    }
}