package org.backend.admin.sse;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/contents")
public class AdminPublishSseController {

    private final AdminPublishSseService sseService;

    @GetMapping(value = "/{contentId}/publish/subscribe", produces = "text/event-stream")
    public SseEmitter subscribe(@PathVariable Long contentId, Authentication authentication) {
        return sseService.subscribe(contentId);
    }
}