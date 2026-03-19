package org.backend.admin.content.publish.controller;

import lombok.RequiredArgsConstructor;
import org.backend.admin.content.publish.service.AdminPublishWaitService;
import org.backend.admin.content.publish.dto.AdminPublishStatusResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/contents")
public class AdminPublishWaitController {

    private final AdminPublishWaitService waitService;

    @GetMapping("/{contentId}/publish/status")
    public AdminPublishStatusResponse status(@PathVariable Long contentId) {
        return waitService.getStatus(contentId);
    }
}