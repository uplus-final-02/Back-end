package org.backend.admin.video.controller;

import lombok.RequiredArgsConstructor;
import org.backend.admin.video.dto.AdminSeriesEpisodeConfirmRequest;
import org.backend.admin.video.dto.AdminSeriesEpisodeConfirmResponse;
import org.backend.admin.video.service.AdminSeriesEpisodeUploadService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/series/{seriesId}/episodes")
public class AdminSeriesEpisodeController {

    private final AdminSeriesEpisodeUploadService adminSeriesEpisodeUploadService;

    @PostMapping("/confirm")
    public AdminSeriesEpisodeConfirmResponse confirm(
            @PathVariable Long seriesId,
            @RequestBody AdminSeriesEpisodeConfirmRequest request
    ) {
        return adminSeriesEpisodeUploadService.confirmUpload(seriesId, request);
    }
}