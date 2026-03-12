package org.backend.admin.video.controller;

import core.security.principal.JwtPrincipal;
import lombok.RequiredArgsConstructor;
import org.backend.admin.video.dto.AdminEpisodePresignRequest;
import org.backend.admin.video.dto.AdminEpisodePresignResponse;
import org.backend.admin.video.service.AdminSeriesEpisodePresignService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/series")
public class AdminSeriesEpisodePresignController {

    private final AdminSeriesEpisodePresignService presignService;

    @PostMapping("/{seriesId}/episodes/{videoId}/presign")
    public AdminEpisodePresignResponse presign(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long seriesId,
            @PathVariable Long videoId,
            @RequestBody AdminEpisodePresignRequest request
    ) {
        return presignService.presignPutUrl(principal, seriesId, videoId, request);
    }
}