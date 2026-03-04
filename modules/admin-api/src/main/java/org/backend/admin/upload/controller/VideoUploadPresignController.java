package org.backend.admin.upload.controller;

import lombok.RequiredArgsConstructor;
import org.backend.admin.upload.dto.VideoUploadPresignRequest;
import org.backend.admin.upload.dto.VideoUploadPresignResponse;
import org.backend.admin.upload.service.VideoUploadPresignService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/uploads/videos")
public class VideoUploadPresignController {

    private final VideoUploadPresignService videoUploadPresignService;

    @PostMapping("/presign")
    public VideoUploadPresignResponse presign(@RequestBody VideoUploadPresignRequest request) {
        return videoUploadPresignService.presign(request);
    }
}