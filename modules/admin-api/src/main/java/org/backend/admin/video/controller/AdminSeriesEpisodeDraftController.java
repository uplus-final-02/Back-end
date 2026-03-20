package org.backend.admin.video.controller;

import core.security.principal.JwtPrincipal;
import lombok.RequiredArgsConstructor;
import org.backend.admin.video.dto.AdminEpisodeDraftResponse;
import org.backend.admin.video.service.AdminSeriesEpisodeDraftService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/series")
public class AdminSeriesEpisodeDraftController {

    private final AdminSeriesEpisodeDraftService draftService;

    @PostMapping("/{seriesId}/episodes/draft")
    public AdminEpisodeDraftResponse createEpisodeDraft(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long seriesId
    ) {
        return draftService.createDraft(principal, seriesId);
    }
}