package org.backend.userapi.content.controller;

import common.enums.ContentAccessLevel;
import common.enums.ContentType;
import core.security.principal.JwtPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.backend.userapi.common.dto.ApiResponse;
import org.backend.userapi.content.dto.ContentDetailResponse;
import org.backend.userapi.content.dto.DefaultContentResponse;
import org.backend.userapi.content.dto.EpisodesResponse;
import org.backend.userapi.content.dto.TrendingResponse;
import org.backend.userapi.content.service.ContentService;
import org.backend.userapi.content.service.TrendingContentService;
import org.backend.userapi.user.dto.response.WatchHistoryListResponse;
import org.backend.userapi.user.service.BookmarkService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Tag(name = "정식 콘텐츠 API", description = "시리즈·영화 목록 조회, 상세 조회, 에피소드 목록, 트렌딩, 홈화면 데이터")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/contents")
public class ContentController {

    private final ContentService contentService;
    private final BookmarkService bookmarkService;
    private final TrendingContentService trendingContentService;

    @Operation(summary = "홈 - 시청 중인 콘텐츠", description = "현재 로그인 유저가 시청 중인 콘텐츠 목록을 반환합니다.")
    @GetMapping("/home/watching-list")
    public ApiResponse<WatchHistoryListResponse> getWatchingContentList(
            @AuthenticationPrincipal JwtPrincipal jwtPrincipal
    ) {
        WatchHistoryListResponse response = contentService.getWatchingContents(jwtPrincipal.getUserId());
        return ApiResponse.success(response);
    }

    @Operation(summary = "홈 - 기본 콘텐츠 목록", description = "제공자(uploaderType), 태그, 접근 등급, 콘텐츠 타입으로 필터링한 콘텐츠 목록을 반환합니다.")
    @GetMapping("/home/default-list")
    public ApiResponse<List<DefaultContentResponse>> getDefaultContentList(
            @RequestParam(required = false, defaultValue = "ADMIN") String uploaderType,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) ContentAccessLevel accessLevel,
            @RequestParam(required = false) ContentType contentType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<DefaultContentResponse> response = contentService.getDefaultContents(uploaderType, tag, accessLevel, contentType, pageable);
        return ApiResponse.success(response);
    }

    @Operation(summary = "홈 - 최근 찜 목록", description = "현재 로그인 유저의 최근 찜(북마크) 콘텐츠 목록을 반환합니다.")
    @GetMapping("/home/bookmark-list")
    public ApiResponse<List<DefaultContentResponse>> getBookmarkList(
            @AuthenticationPrincipal JwtPrincipal jwtPrincipal
    ) {
        // userPrincipal이 null이 아닐 때만 동작 (Security 설정에 따라 다름)
        return ApiResponse.success(bookmarkService.getRecentBookmarkList(jwtPrincipal.getUserId()));
    }

    @Operation(summary = "콘텐츠 상세 조회", description = "contentId로 시리즈 또는 영화의 상세 정보를 조회합니다.")
    @GetMapping("/{contentId}")
    public ResponseEntity<ContentDetailResponse> getContentDetail(@Parameter(description = "콘텐츠 ID") @PathVariable Long contentId) {
        return ResponseEntity.ok(contentService.getContentDetail(contentId));
    }

    @Operation(summary = "에피소드 목록 조회", description = "시리즈의 전체 에피소드 목록을 조회합니다.")
    @GetMapping("/{contentId}/episodes-list")
    public ResponseEntity<EpisodesResponse> getContentEpisodeList(@Parameter(description = "시리즈 콘텐츠 ID") @PathVariable Long contentId) {
        return ResponseEntity.ok(contentService.getContentEpisodes(contentId));
    }

    @Operation(summary = "홈 - 트렌딩 콘텐츠", description = "조회수·북마크 기반으로 계산된 인기 콘텐츠 순위를 반환합니다.")
    @GetMapping("/home/trending")
    public ApiResponse<List<TrendingResponse>> getTrendingContents(
            @RequestParam(defaultValue = "10") int limit
    ) {
        return new ApiResponse<>(200, "인기 차트 조회 성공", trendingContentService.getTrendingList(limit));
    }

    @GetMapping("/test/trending/run")
    public String triggerTrending() {
        // 현재 시각 기준으로 트렌딩 강제 계산
        trendingContentService.calculateTrendingScores(LocalDateTime.now().truncatedTo(ChronoUnit.HOURS));
        return "트렌딩 차트 갱신 완료";
    }

}