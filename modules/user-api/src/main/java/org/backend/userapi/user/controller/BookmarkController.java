package org.backend.userapi.user.controller;

import lombok.RequiredArgsConstructor;
<<<<<<< HEAD:modules/user-api/src/main/java/org/backend/userapi/interaction/controller/BookmarkController.java
import org.backend.userapi.interaction.dto.response.BookmarkListResponse;
import org.backend.userapi.interaction.service.BookmarkService;
import org.backend.userapi.auth.jwt.UserPrincipal;
=======

import org.backend.userapi.user.dto.response.BookmarkListResponse;
import org.backend.userapi.user.service.BookmarkService;
import org.backend.userapi.auth.dto.UserPrincipal;
>>>>>>> origin/develop:modules/user-api/src/main/java/org/backend/userapi/user/controller/BookmarkController.java
import org.backend.userapi.common.dto.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
// 클래스 레벨에는 경로를 걸지 않고 메서드에 직접 명시하여 혼동을 방지합니다.
public class BookmarkController {

    private final BookmarkService bookmarkService;

    // [1] 찜하기 등록
    // URL: http://localhost:8081/api/histories/bookmarks/{contentId}
    @PostMapping("/api/histories/bookmarks/{contentId}")
    public ApiResponse<Void> addBookmark(
            @PathVariable Long contentId,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        bookmarkService.addBookmark(userPrincipal.getUserId(), contentId);
        return new ApiResponse<>(200, "Success", null);
    }

    // [2] 찜 목록 조회 (수정됨: /api 추가)
    // URL: http://localhost:8081/api/users/me/bookmarks
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