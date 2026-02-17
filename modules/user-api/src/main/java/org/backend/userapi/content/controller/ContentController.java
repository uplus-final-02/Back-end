package org.backend.userapi.content.controller;

import lombok.RequiredArgsConstructor;
import org.backend.userapi.common.dto.ApiResponse;
import org.backend.userapi.content.dto.DefaultContentResponse;
import org.backend.userapi.content.dto.WatchingContentResponse;
import org.backend.userapi.content.service.ContentService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/contents")
public class ContentController {

    private final ContentService contentService;

    @GetMapping("/home/watching")
    public ApiResponse<List<WatchingContentResponse>> getWatchingContents(
        @AuthenticationPrincipal Long userId
    ) {
        List<WatchingContentResponse> response = contentService.getWatchingContents(userId);
        return ApiResponse.success(response);
    }

    @GetMapping("/home/basic")
    public ApiResponse<List<DefaultContentResponse>> getContents(
            @RequestParam(required = false) String category
    ) {
        List<DefaultContentResponse> response = contentService.getContents(category);
        return ApiResponse.success(response);
    }
}
