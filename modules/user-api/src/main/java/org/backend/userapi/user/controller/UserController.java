package org.backend.userapi.user.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.backend.userapi.common.dto.ApiResponse;
import org.backend.userapi.user.dto.response.NicknameUpdateResponse;
import org.backend.userapi.user.service.UserService;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

  private final UserService userService;

  // PATCH /api/users/nickname?userId=1&nickname=새닉네임
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
}