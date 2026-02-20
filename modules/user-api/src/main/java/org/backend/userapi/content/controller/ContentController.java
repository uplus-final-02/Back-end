package org.backend.userapi.content.controller;

import lombok.RequiredArgsConstructor;

import org.backend.userapi.auth.jwt.UserPrincipal;
import org.backend.userapi.common.dto.ApiResponse;
import org.backend.userapi.content.dto.DefaultContentResponse;
import org.backend.userapi.content.dto.ContentDetailResponse;
import org.backend.userapi.content.dto.EpisodesResponse;
import org.backend.userapi.content.dto.WatchingContentResponse;
import org.backend.userapi.content.service.ContentService;
import org.backend.userapi.user.dto.response.RecentBookmarkResponse;
import org.backend.userapi.user.service.BookmarkService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/contents")
public class ContentController {

    private final ContentService contentService;
    private final BookmarkService bookmarkService;

    // 1. 시청 중인 콘텐츠
    @GetMapping("/home/watching")
    public ApiResponse<List<WatchingContentResponse>> getWatchingContents(
        @AuthenticationPrincipal Long userId
    ) {
        List<WatchingContentResponse> response = contentService.getWatchingContents(userId);
        return ApiResponse.success(response);
    }

    // 2. 기본 콘텐츠 목록 (카테고리별)
    @GetMapping("/home/basic")
    public ApiResponse<List<DefaultContentResponse>> getContents(
            @RequestParam(required = false) String category
    ) {
        List<DefaultContentResponse> response = contentService.getContents(category);
        return ApiResponse.success(response);
    }

    // 3. 최근 찜 목록
    @GetMapping("/home/bookmark-list")
    public ApiResponse<List<RecentBookmarkResponse>> getBookmarkList(
        @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        // userPrincipal이 null이 아닐 때만 동작 (Security 설정에 따라 다름)
        return ApiResponse.success(bookmarkService.getRecentBookmarkList(userPrincipal.getUserId()));
    }

    // 4. 콘텐츠 상세 조회
    @GetMapping("/{contentId}")
    public ResponseEntity<ContentDetailResponse> getContentDetail(@PathVariable Long contentId) {
        return ResponseEntity.ok(contentService.getContentDetail(contentId));
    }

    // 5. 에피소드 조회
    @GetMapping("/{contentId}/episodes")
    public ResponseEntity<EpisodesResponse> getContentEpisodes(@PathVariable Long contentId) {
        return ResponseEntity.ok(contentService.getContentEpisodes(contentId));
    }
} 