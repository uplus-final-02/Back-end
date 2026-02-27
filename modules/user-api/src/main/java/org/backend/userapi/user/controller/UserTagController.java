package org.backend.userapi.user.controller;


import org.backend.userapi.common.dto.ApiResponse;

import org.backend.userapi.user.dto.request.PreferredTagUpdateRequest;
import org.backend.userapi.user.service.UserTagPreferenceService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import core.security.principal.JwtPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "태그 API", description = "사용자 선호 태그 관리")
@RestController
@RequiredArgsConstructor
public class UserTagController {

    private final UserTagPreferenceService userTagPreferenceService;

    // URL: http://localhost:8081/api/users/me/preferred-tags
    @Operation(summary = "내 선호 태그 변경", description = "사용자의 선호 태그를 새로운 목록으로 덮어씁니다.")
    @PutMapping("/api/users/me/preferred-tags")
    public ApiResponse<Void> updatePreferredTags(
            @AuthenticationPrincipal JwtPrincipal jwtPrincipal,
            @RequestBody PreferredTagUpdateRequest request
    ) {
        userTagPreferenceService.updatePreferredTags(jwtPrincipal.getUserId(), request);
        return new ApiResponse<>(200, "선호 태그 변경 성공", null);
    }
}