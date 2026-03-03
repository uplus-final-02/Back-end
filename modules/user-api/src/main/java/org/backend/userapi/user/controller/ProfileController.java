package org.backend.userapi.user.controller;

import org.backend.userapi.common.dto.ApiResponse;
import org.backend.userapi.user.dto.response.ProfileResponse;
import org.backend.userapi.common.service.S3UploadService;
import org.backend.userapi.user.service.ProfileService;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import core.security.principal.JwtPrincipal;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {
  private final ProfileService userService;
  private final S3UploadService s3UploadService;

  @GetMapping("/mypage")
  public ApiResponse<ProfileResponse> getMyProfile(
      // TODO: 실제로는 @AuthenticationPrincipal 등을 사용해 토큰에서 ID를 가져와야 합니다.
      @RequestParam(name = "userId", defaultValue = "1") Long userId
  ) {
    ProfileResponse response = userService.getMyProfile(userId);
    return ApiResponse.success(response);
  }
  
  @PatchMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ApiResponse<ProfileResponse> updateProfileImage(
      @AuthenticationPrincipal JwtPrincipal jwtPrincipal,
      @RequestPart("profileImage") MultipartFile profileImage // 💡 프론트에서 넘어올 파일 파라미터명
  ) {
      Long userId = jwtPrincipal.getUserId();
      
      // 1. MinIO에 파일 업로드하고 저장된 이미지 URL 받아오기
      String uploadedImageUrl = s3UploadService.uploadProfileImage(profileImage);
      
      // 2. 받아온 URL을 DB 유저 정보에 업데이트하기
      ProfileResponse response = userService.updateProfileImage(userId, uploadedImageUrl);
      
      return ApiResponse.success(response);
  }
}
