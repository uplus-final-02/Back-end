package org.backend.userapi.user.controller;

import org.backend.userapi.common.dto.ApiResponse;
import org.backend.userapi.user.dto.response.BookmarkListResponse;
import org.backend.userapi.user.dto.response.BookmarkPlaylistResponse;
import org.backend.userapi.user.service.BookmarkService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import core.security.principal.JwtPrincipal;
import lombok.RequiredArgsConstructor;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "3. 찜(북마크) API", description = "콘텐츠 찜하기, 찜 취소, 찜 목록 조회 및 연속 재생 정보 API")
@RestController
@RequiredArgsConstructor
public class BookmarkController {

    private final BookmarkService bookmarkService;

    @Operation(summary = "찜하기 (등록)", description = "특정 콘텐츠를 내 찜 목록에 추가합니다.")
    @PostMapping("/api/histories/bookmarks/{contentId}")
    public ApiResponse<Void> addBookmark(
            @Parameter(description = "찜할 콘텐츠의 고유 ID", example = "18") @PathVariable Long contentId,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtPrincipal jwtPrincipal
    ) {
        bookmarkService.addBookmark(jwtPrincipal.getUserId(), contentId);
        return new ApiResponse<>(200, "Success", null);
    }

    @Operation(summary = "찜 목록 조회 (무한 스크롤)", description = "내가 찜한 콘텐츠 목록을 조회합니다. Cursor 기반 페이징을 사용합니다.")
    @GetMapping("/api/users/me/bookmarks")
    public ApiResponse<BookmarkListResponse> getBookmarks(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtPrincipal jwtPrincipal,
            @Parameter(description = "다음 페이지 조회를 위한 커서 ID (첫 요청 시 비워둠)", example = "45") 
            @RequestParam(required = false) Long cursor,
            @Parameter(description = "한 번에 가져올 데이터 개수", example = "10") 
            @RequestParam(defaultValue = "10") int size
    ) {
        BookmarkListResponse data = bookmarkService.getMyBookmarks(jwtPrincipal.getUserId(), cursor, size);
        return new ApiResponse<>(200, "찜 목록 조회 성공", data);
    }
    
    @Operation(summary = "찜하기 취소 (삭제)", description = "찜 목록에서 특정 콘텐츠를 제거합니다.")
    @DeleteMapping("/api/users/me/bookmarks/{contentId}")
    public ApiResponse<Void> removeBookmark(
            @Parameter(description = "찜 취소할 콘텐츠의 고유 ID", example = "18") @PathVariable Long contentId,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtPrincipal jwtPrincipal
    ){
        bookmarkService.removeBookmark(jwtPrincipal.getUserId(), contentId);
        return new ApiResponse<Void>(200, "찜 목록에서 삭제되었습니다.", null);
    }
    
    @Operation(summary = "찜 목록 연속 재생 (플레이리스트)", description = "찜한 콘텐츠들을 유튜브 플레이리스트처럼 연속해서 볼 수 있도록 시청 기록과 묶어서 에피소드 단위로 내려줍니다.")
    @GetMapping("/api/users/me/bookmarks/playlist")
    public ApiResponse<BookmarkPlaylistResponse> getBookmarkPlaylist(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtPrincipal jwtPrincipal
    ) {
        BookmarkPlaylistResponse data = bookmarkService.getBookmarkPlaylist(jwtPrincipal.getUserId());
        return new ApiResponse<>(200, "찜 목록 연속 재생 정보 조회 성공", data);
    }
}