package org.backend.userapi.content.controller;

import org.backend.userapi.common.dto.ApiResponse;
import org.backend.userapi.content.dto.UserContentDeleteResponse;
import org.backend.userapi.content.dto.UserContentUpdateRequest;
import org.backend.userapi.content.dto.UserContentUpdateResponse;
import org.backend.userapi.content.dto.UserThumbnailUploadResponse;
import org.backend.userapi.content.service.UserContentService;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
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
}