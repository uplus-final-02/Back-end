package org.backend.userapi.user.controller;

import org.backend.userapi.common.dto.ApiResponse;
import org.backend.userapi.user.dto.response.BookmarkListResponse;
import org.backend.userapi.user.service.BookmarkService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import core.security.principal.JwtPrincipal;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class BookmarkController {

    private final BookmarkService bookmarkService;

    @PostMapping("/api/histories/bookmarks/{contentId}")
    public ApiResponse<Void> addBookmark(
            @PathVariable Long contentId,
            @AuthenticationPrincipal JwtPrincipal jwtPrincipal
    ) {
        bookmarkService.addBookmark(jwtPrincipal.getUserId(), contentId);
        return new ApiResponse<>(200, "Success", null);
    }

    @GetMapping("/api/users/me/bookmarks")
    public ApiResponse<BookmarkListResponse> getBookmarks(
            @AuthenticationPrincipal JwtPrincipal jwtPrincipal,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "10") int size
    ) {
        BookmarkListResponse data = bookmarkService.getMyBookmarks(jwtPrincipal.getUserId(), cursor, size);
        return new ApiResponse<>(200, "찜 목록 조회 성공", data);
    }
}