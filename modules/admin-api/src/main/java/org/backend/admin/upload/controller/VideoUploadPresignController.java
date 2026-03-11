package org.backend.admin.upload.controller;

import core.security.principal.JwtPrincipal;
import lombok.RequiredArgsConstructor;
import org.backend.admin.upload.dto.VideoUploadPresignRequest;
import org.backend.admin.upload.dto.VideoUploadPresignResponse;
import org.backend.admin.upload.service.VideoUploadPresignService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/uploads/videos")
public class VideoUploadPresignController {

    private final VideoUploadPresignService videoUploadPresignService;

    @PostMapping("/presign")
    public VideoUploadPresignResponse presign(
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestBody VideoUploadPresignRequest request
    ) {
        return videoUploadPresignService.presign(principal, request);
    }
}