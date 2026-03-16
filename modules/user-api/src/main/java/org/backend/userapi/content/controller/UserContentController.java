package org.backend.userapi.content.controller;

import core.security.principal.JwtPrincipal;
import lombok.RequiredArgsConstructor;
import org.backend.userapi.common.dto.ApiResponse;
import org.backend.userapi.content.dto.UserContentDeleteResponse;
import org.backend.userapi.content.dto.UserContentUpdateRequest;
import org.backend.userapi.content.dto.UserContentUpdateResponse;
import org.backend.userapi.content.service.UserContentService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/user/contents")
public class UserContentController {

    private final UserContentService userContentService;

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
}