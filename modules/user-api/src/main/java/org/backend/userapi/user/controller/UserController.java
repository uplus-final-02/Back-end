package org.backend.userapi.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import core.security.principal.JwtPrincipal;

import org.backend.userapi.common.dto.ApiResponse;
import org.backend.userapi.user.dto.request.WithdrawRequest;
import org.backend.userapi.user.dto.response.NicknameUpdateResponse;
import org.backend.userapi.user.service.UserService;

@Tag(name = "회원 API", description = "닉네임 변경, 회원 탈퇴(Soft Delete)")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

  private final UserService userService;

  @Operation(summary = "닉네임 변경")
  @PatchMapping("/nickname")
  public ApiResponse<NicknameUpdateResponse> updateNickname(
      // TODO: 실제 배포 시 @AuthenticationPrincipal 사용
      @RequestParam(name = "userId", defaultValue = "1") Long userId,
      @RequestParam(name = "nickname") String nickname
  ) {
    // 간단한 유효성 검사
    if (nickname == null || nickname.trim().isEmpty()) {
      throw new IllegalArgumentException("닉네임을 입력해주세요.");
    }

    // 서비스 호출 및 응답 반환
    NicknameUpdateResponse response = userService.updateNickname(userId, nickname);

    return ApiResponse.success(response);
  }
  
  @Operation(summary = "회원 탈퇴", description = "Soft Delete 방식으로 탈퇴 처리합니다. deletedAt이 기록되고 상태가 WITHDRAW_PENDING으로 변경됩니다.")
  @DeleteMapping("/me")
  public ApiResponse<Void> withdraw(
          @AuthenticationPrincipal JwtPrincipal principal,
          @RequestBody WithdrawRequest request
  ) {

      userService.withdraw(principal.getUserId(), request.getReason());

      return ApiResponse.success(null);
  }
}