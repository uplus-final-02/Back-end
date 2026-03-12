package org.backend.admin.video.controller;

import core.security.principal.JwtPrincipal;
import lombok.RequiredArgsConstructor;
import org.backend.admin.video.dto.AdminVideoUploadConfirmRequest;
import org.backend.admin.video.dto.AdminVideoUploadConfirmResponse;
import org.backend.admin.video.service.AdminVideoUploadService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/videos")
public class AdminVideoController {

    private final AdminVideoUploadService adminVideoUploadService;

    @PostMapping("/confirm")
    public AdminVideoUploadConfirmResponse confirmUpload(
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestBody AdminVideoUploadConfirmRequest request
    ) {
        return adminVideoUploadService.confirmUpload(principal, request);
    }
}