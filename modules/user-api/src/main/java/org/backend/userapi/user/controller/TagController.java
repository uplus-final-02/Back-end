package org.backend.userapi.user.controller; // 패키지명 확인!

import lombok.RequiredArgsConstructor;
import org.backend.userapi.auth.dto.UserPrincipal;
import org.backend.userapi.common.dto.ApiResponse;
import org.backend.userapi.user.dto.request.PreferredTagUpdateRequest;
import org.backend.userapi.user.service.UserTagPreferenceService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class TagController {

    private final UserTagPreferenceService userTagPreferenceService;

    // URL: http://localhost:8081/api/users/me/preferred-tags
    @PutMapping("/api/users/me/preferred-tags")
    public ApiResponse<Void> updatePreferredTags(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestBody PreferredTagUpdateRequest request
    ) {
        userTagPreferenceService.updatePreferredTags(userPrincipal.getUserId(), request);
        return new ApiResponse<>(200, "선호 태그 변경 성공", null);
    }
}