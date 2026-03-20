package org.backend.admin.video.controller;

import core.security.principal.JwtPrincipal;
import lombok.RequiredArgsConstructor;
import org.backend.admin.video.dto.AdminSeriesEpisodeConfirmRequest;
import org.backend.admin.video.dto.AdminSeriesEpisodeConfirmResponse;
import org.backend.admin.video.service.AdminSeriesEpisodeUploadService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/series/{seriesId}/episodes")
public class AdminSeriesEpisodeController {

    private final AdminSeriesEpisodeUploadService adminSeriesEpisodeUploadService;

    @PostMapping("/confirm")
    public AdminSeriesEpisodeConfirmResponse confirm(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long seriesId,
            @RequestBody AdminSeriesEpisodeConfirmRequest request
    ) {
        return adminSeriesEpisodeUploadService.confirmUpload(principal, seriesId, request);
    }
}