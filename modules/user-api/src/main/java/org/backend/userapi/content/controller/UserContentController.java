package org.backend.userapi.content.controller;

import org.backend.userapi.common.dto.ApiResponse;
import org.backend.userapi.content.dto.UserContentDeleteResponse;
import org.backend.userapi.content.dto.UserContentPlayResponse;
import org.backend.userapi.content.dto.UserContentUpdateRequest;
import org.backend.userapi.content.dto.UserContentUpdateResponse;
import org.backend.userapi.content.dto.UserThumbnailUploadResponse;
import org.backend.userapi.content.service.UserContentService;
import org.backend.userapi.content.service.UserWatchHistoryService;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import core.security.principal.JwtPrincipal;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/user/contents")
public class UserContentController {

    private final UserContentService userContentService;
    private final UserWatchHistoryService userWatchHistoryService;

    @PutMapping("/{userContentId}/metadata")
    public ApiResponse<UserContentUpdateResponse> updateMetadata(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long userContentId,
            @RequestBody UserContentUpdateRequest request
    ) {
        return ApiResponse.ok("유저 콘텐츠 메타데이터 수정 성공",
                userContentService.updateMetadata(principal, userContentId, request));
    }

    @DeleteMapping("/{userContentId}")
    public ApiResponse<UserContentDeleteResponse> delete(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long userContentId
    ) {
        return ApiResponse.ok("유저 콘텐츠 삭제 성공",
                userContentService.delete(principal, userContentId));
    }
    
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
     * 유저 업로드 콘텐츠 재생 정보 조회.
     *
     * <p>서명된 HLS URL을 반환한다.
     * 로컬(MinIO): /api/hls/{fileId}/master.m3u8?expires=...&signature=...
     * 운영(CloudFront): https://cdn.../hls/{fileId}/master.m3u8?...
     *
     * GET /api/user/contents/{userContentId}/play
     */
    @GetMapping("/{userContentId}/play")
    public ApiResponse<UserContentPlayResponse> play(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long userContentId
    ) {
        return ApiResponse.ok("재생 정보 조회 성공",
                userContentService.play(principal, userContentId));
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
    @PostMapping("/{userContentId}/watch")
    public ApiResponse<Void> recordWatch(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long userContentId
    ) {
        if (principal == null || principal.getUserId() == null) {
            throw new IllegalArgumentException("LOGIN_REQUIRED");
        }
        userWatchHistoryService.upsertWatchHistory(principal.getUserId(), userContentId);
        return ApiResponse.ok("시청 기록 저장 성공", null);
    }
}