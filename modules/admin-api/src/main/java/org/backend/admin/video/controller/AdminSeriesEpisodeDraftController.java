package org.backend.admin.video.controller;

import lombok.RequiredArgsConstructor;
import org.backend.admin.video.dto.AdminEpisodeDraftResponse;
import org.backend.admin.video.service.AdminSeriesEpisodeDraftService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/series")
public class AdminSeriesEpisodeDraftController {

    private final AdminSeriesEpisodeDraftService draftService;

    @PostMapping("/{seriesId}/episodes/draft")
    public AdminEpisodeDraftResponse createEpisodeDraft(@PathVariable Long seriesId) {
        return draftService.createDraft(seriesId);
    }
}