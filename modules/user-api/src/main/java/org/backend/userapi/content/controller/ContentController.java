package org.backend.userapi.content.controller;

import lombok.RequiredArgsConstructor;
import org.backend.userapi.auth.dto.UserPrincipal;
import org.backend.userapi.common.dto.ApiResponse;
import org.backend.userapi.content.dto.DefaultContentResponse;
import org.backend.userapi.content.dto.ContentDetailResponse;
import org.backend.userapi.content.dto.EpisodesResponse;
import org.backend.userapi.content.dto.WatchingContentResponse;
import org.backend.userapi.content.service.ContentService;
import org.backend.userapi.interaction.dto.response.RecentBookmarkResponse;
import org.backend.userapi.interaction.service.BookmarkService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/contents")
public class ContentController {

    private final ContentService contentService;
    private final BookmarkService bookmarkService;

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
    @GetMapping("/home/bookmark-list")
    public ApiResponse<List<RecentBookmarkResponse>> getBookmarkList(
        @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        return ApiResponse.success(bookmarkService.getRecentBookmarkList(userPrincipal.getUserId()));
    }
}

    @GetMapping("/{contentId}")
    public ResponseEntity<ContentDetailResponse> getContentDetail(@PathVariable Long contentId) {
        return ResponseEntity.ok(contentService.getContentDetail(contentId));
    }

    @GetMapping("/{contentId}/episodes")
    public ResponseEntity<EpisodesResponse> getContentEpisodes(@PathVariable Long contentId) {
        return ResponseEntity.ok(contentService.getContentEpisodes(contentId));
    }
}
