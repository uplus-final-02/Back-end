package org.backend.userapi.user.controller;

import lombok.RequiredArgsConstructor;
import org.backend.userapi.common.dto.ApiResponse;
import org.backend.userapi.user.dto.response.BookmarkListResponse;
import org.backend.userapi.user.service.BookmarkService;
import org.backend.userapi.auth.jwt.UserPrincipal; // ✅ SecurityContext principal
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class BookmarkController {

    private final BookmarkService bookmarkService;

    @PostMapping("/api/histories/bookmarks/{contentId}")
    public ApiResponse<Void> addBookmark(
            @PathVariable Long contentId,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        bookmarkService.addBookmark(userPrincipal.getUserId(), contentId);
        return new ApiResponse<>(200, "Success", null);
    }

    @GetMapping("/api/users/me/bookmarks")
    public ApiResponse<BookmarkListResponse> getBookmarks(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "10") int size
    ) {
        BookmarkListResponse data = bookmarkService.getMyBookmarks(userPrincipal.getUserId(), cursor, size);
        return new ApiResponse<>(200, "찜 목록 조회 성공", data);
    }
}