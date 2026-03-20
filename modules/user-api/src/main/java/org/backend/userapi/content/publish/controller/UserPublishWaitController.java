// modules/user-api/src/main/java/org/backend/userapi/content/publish/controller/UserPublishWaitController.java
package org.backend.userapi.content.publish.controller;

import core.security.principal.JwtPrincipal;
import lombok.RequiredArgsConstructor;
import org.backend.userapi.content.publish.dto.UserPublishStatusResponse;
import org.backend.userapi.content.publish.service.UserPublishWaitService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/user/contents")
public class UserPublishWaitController {

    private final UserPublishWaitService waitService;

    @GetMapping("/{userContentId}/publish/status")
    public UserPublishStatusResponse status(
            @PathVariable Long userContentId,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        if (principal == null || principal.getUserId() == null) {
            throw new IllegalArgumentException("LOGIN_REQUIRED");
        }
        return waitService.getStatus(userContentId);
    }
}