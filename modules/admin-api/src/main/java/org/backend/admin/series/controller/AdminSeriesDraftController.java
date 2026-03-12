package org.backend.admin.series.controller;

import core.security.principal.JwtPrincipal;
import lombok.RequiredArgsConstructor;
import org.backend.admin.series.dto.AdminSeriesDraftCreateResponse;
import org.backend.admin.series.service.AdminSeriesDraftService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/series")
public class AdminSeriesDraftController {

    private final AdminSeriesDraftService adminSeriesDraftService;

    @PostMapping("/draft")
    public AdminSeriesDraftCreateResponse createSeriesDraft(
            @AuthenticationPrincipal JwtPrincipal principal,
            Authentication authentication
    ) {
        return adminSeriesDraftService.createDraft(principal, authentication);
    }
}