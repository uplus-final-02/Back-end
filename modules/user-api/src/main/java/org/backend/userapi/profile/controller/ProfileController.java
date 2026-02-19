package org.backend.userapi.profile.controller;

import lombok.RequiredArgsConstructor;
import org.backend.userapi.common.dto.ApiResponse;
import org.backend.userapi.profile.dto.ProfileDto;
import org.backend.userapi.profile.service.ProfileService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {
  private final ProfileService userService;

  @GetMapping("/mypage")
  public ApiResponse<ProfileDto> getMyProfile(
      // TODO: 실제로는 @AuthenticationPrincipal 등을 사용해 토큰에서 ID를 가져와야 합니다.
      @RequestParam(name = "userId", defaultValue = "1") Long userId
  ) {
    ProfileDto response = userService.getMyProfile(userId);
    return ApiResponse.success(response);
  }
}
