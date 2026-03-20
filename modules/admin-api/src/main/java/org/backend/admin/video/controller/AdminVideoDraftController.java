package org.backend.admin.video.controller;

import core.security.principal.JwtPrincipal;
import lombok.RequiredArgsConstructor;
import org.backend.admin.video.dto.AdminVideoDraftCreateResponse;
import org.backend.admin.video.service.AdminVideoDraftService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/videos")
public class AdminVideoDraftController {

    private final AdminVideoDraftService adminVideoDraftService;

    @PostMapping("/draft")
    public AdminVideoDraftCreateResponse createVideoDraft(
            @AuthenticationPrincipal JwtPrincipal principal,
            Authentication authentication
    ) {
        return adminVideoDraftService.createDraft(principal, authentication);
    }
}