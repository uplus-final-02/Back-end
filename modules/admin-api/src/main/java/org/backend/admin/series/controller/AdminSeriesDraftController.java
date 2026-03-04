package org.backend.admin.series.controller;

import lombok.RequiredArgsConstructor;
import org.backend.admin.series.dto.AdminSeriesDraftCreateRequest;
import org.backend.admin.series.dto.AdminSeriesDraftCreateResponse;
import org.backend.admin.series.service.AdminSeriesDraftService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/series")
public class AdminSeriesDraftController {

    private final AdminSeriesDraftService adminSeriesDraftService;

    @PostMapping("/draft")
    public AdminSeriesDraftCreateResponse createDraft(@RequestBody AdminSeriesDraftCreateRequest request) {
        return adminSeriesDraftService.createDraft(request);
    }
}