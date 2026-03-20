package org.backend.admin.content.publish.controller;

import lombok.RequiredArgsConstructor;
import org.backend.admin.content.publish.service.AdminContentPublishService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/contents")
public class AdminContentPublishController {

    private final AdminContentPublishService publishService;

    @PostMapping("/{contentId}/publish/request")
    public void request(@PathVariable Long contentId) {
        publishService.requestPublish(contentId);
    }

    @PostMapping("/{contentId}/publish/cancel")
    public void cancel(@PathVariable Long contentId) {
        publishService.cancelPublish(contentId);
    }
}