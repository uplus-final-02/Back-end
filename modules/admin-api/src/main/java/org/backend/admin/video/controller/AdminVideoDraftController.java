package org.backend.admin.video.controller;

import lombok.RequiredArgsConstructor;
import org.backend.admin.video.dto.AdminVideoDraftCreateRequest;
import org.backend.admin.video.dto.AdminVideoDraftCreateResponse;
import org.backend.admin.video.service.AdminVideoDraftService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/videos")
public class AdminVideoDraftController {

    private final AdminVideoDraftService adminVideoDraftService;

    @PostMapping("/draft")
    public AdminVideoDraftCreateResponse createDraft(@RequestBody AdminVideoDraftCreateRequest request) {
        return adminVideoDraftService.createDraft(request);
    }
}