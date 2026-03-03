package org.backend.userapi.user.controller;

import core.security.principal.JwtPrincipal;
import lombok.RequiredArgsConstructor;
import org.backend.userapi.user.dto.response.WatchHistoryListResponse;
import org.backend.userapi.user.dto.response.WatchStatisticsResponse;
import org.backend.userapi.user.service.WatchHistoryService;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.backend.userapi.common.dto.ApiResponse;

@RestController
@RequestMapping("/api/users/me/watch-history")
@RequiredArgsConstructor
public class WatchHistoryController {

  private final WatchHistoryService watchHistoryService;

  @GetMapping
  public ApiResponse<WatchHistoryListResponse> getMyWatchHistory(
      @RequestParam(name = "cursor", required = false) Long cursor,
      @RequestParam(name = "size", defaultValue = "20") int size,
      @AuthenticationPrincipal JwtPrincipal jwtPrincipal
  ) {
    // 토큰에서 유저 ID 추출
    Long userId = jwtPrincipal.getUserId();

    // 페이징 설정
    PageRequest pageRequest = PageRequest.of(0, size);

    // 서비스 호출
    WatchHistoryListResponse response = watchHistoryService.getWatchHistories(userId, cursor, pageRequest);

    // 결과 반환
    return ApiResponse.success(response);
  }

  @DeleteMapping("/{historyId}")
  public ApiResponse<Void> deleteWatchHistory(
      @PathVariable("historyId") Long historyId,
      @AuthenticationPrincipal JwtPrincipal jwtPrincipal
  ) {
    Long userId = jwtPrincipal.getUserId();

    // 삭제 로직 실행
    watchHistoryService.deleteWatchHistory(userId, historyId);

    // 성공 메시지와 함께 null 데이터 반환
    return ApiResponse.success(null);
  }

  @GetMapping("/statistics")
  public ApiResponse<WatchStatisticsResponse> getWatchStatistics(
      @AuthenticationPrincipal JwtPrincipal jwtPrincipal
  ) {
    Long userId = jwtPrincipal.getUserId();

    // 서비스에서 통계 데이터 생성
    WatchStatisticsResponse response = watchHistoryService.getWatchStatistics(userId);

    return ApiResponse.success(response);
  }
}