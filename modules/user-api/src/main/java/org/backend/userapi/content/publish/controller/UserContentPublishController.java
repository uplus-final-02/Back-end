package org.backend.userapi.content.publish.controller;

import core.security.principal.JwtPrincipal;
import lombok.RequiredArgsConstructor;
import org.backend.userapi.content.publish.service.UserContentPublishService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/user/contents")
public class UserContentPublishController {

    private final UserContentPublishService publishService;

    @PostMapping("/{contentId}/publish/request")
    public void request(@PathVariable Long contentId, @AuthenticationPrincipal JwtPrincipal principal) {
        publishService.requestPublish(contentId, principal);
    }

    @PostMapping("/{contentId}/publish/cancel")
    public void cancel(@PathVariable Long contentId, @AuthenticationPrincipal JwtPrincipal principal) {
        publishService.cancelPublish(contentId, principal);
    }
}