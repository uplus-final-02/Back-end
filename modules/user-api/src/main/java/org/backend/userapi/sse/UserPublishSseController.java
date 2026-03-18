// modules/user-api/src/main/java/org/backend/userapi/sse/UserPublishSseController.java
package org.backend.userapi.sse;

import core.security.principal.JwtPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/user/contents")
public class UserPublishSseController {

    private final UserPublishSseService sseService;

    @GetMapping(value = "/{userContentId}/publish/subscribe", produces = "text/event-stream")
    public SseEmitter subscribe(
            @PathVariable Long userContentId,
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        if (principal == null || principal.getUserId() == null) {
            throw new IllegalArgumentException("LOGIN_REQUIRED");
        }
        return sseService.subscribe(userContentId);
    }
}