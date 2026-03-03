package org.backend.userapi.user.controller;

import core.storage.ObjectStorageService; 
import org.backend.userapi.common.dto.ApiResponse;
import org.backend.userapi.user.dto.response.ProfileResponse;
import org.backend.userapi.user.service.ProfileService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import core.security.principal.JwtPrincipal;
import lombok.RequiredArgsConstructor;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {
  
  private final ProfileService userService;
  private final ObjectStorageService objectStorageService;

  @GetMapping("/mypage")
  public ApiResponse<ProfileResponse> getMyProfile(
      @RequestParam(name = "userId", defaultValue = "1") Long userId
  ) {
    ProfileResponse response = userService.getMyProfile(userId);
    return ApiResponse.success(response);
  }

  @GetMapping("/image/presigned-url")
  public ApiResponse<Map<String, String>> getProfilePresignedUrl(
      @AuthenticationPrincipal JwtPrincipal jwtPrincipal,
      @RequestParam(name = "extension", defaultValue = ".png") String extension
  ) {
      Long userId = jwtPrincipal.getUserId();
      
      String key = objectStorageService.buildObjectKey("images/profile", userId, "profile" + extension);
      
      String contentType = extension.endsWith("jpg") || extension.endsWith("jpeg") ? "image/jpeg" : "image/png";
      var presignedUrl = objectStorageService.generatePutPresignedUrl(key, contentType, Duration.ofMinutes(10));
      
      return ApiResponse.success(Map.of(
          "uploadUrl", presignedUrl.url().toString(),
          "objectKey", presignedUrl.objectKey()
      ));
  }

  @PatchMapping("/image")
  public ApiResponse<ProfileResponse> updateProfileImage(
      @AuthenticationPrincipal JwtPrincipal jwtPrincipal,
      @RequestBody Map<String, String> request 
  ) {
      Long userId = jwtPrincipal.getUserId();
      String objectKey = request.get("objectKey");
      
      ProfileResponse response = userService.updateProfileImage(userId, objectKey);      
      
      return ApiResponse.success(response);
  }
}