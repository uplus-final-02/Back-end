package org.backend.userapi.user.controller;

import core.storage.ObjectStorageService; 
import org.backend.userapi.common.dto.ApiResponse;
import org.backend.userapi.user.dto.response.ProfileResponse;
import org.backend.userapi.user.service.ProfileService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import core.security.principal.JwtPrincipal;
import lombok.RequiredArgsConstructor;

// 💡 Swagger 어노테이션 추가
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Content;

import java.time.Duration;
import java.util.Map;

@Tag(name = "2. 프로필 API", description = "마이페이지 조회 및 프로필 이미지 변경 API")
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {
  
  private final ProfileService userService;
  private final ObjectStorageService objectStorageService;

  @Operation(
      summary = "내 프로필 조회", 
      description = "마이페이지에 표시할 내 프로필 정보와 선호 태그, 구독 상태 등을 조회합니다."
  )
  @GetMapping("/mypage")
  public ApiResponse<ProfileResponse> getMyProfile(
      // 💡 보안: RequestParam 대신 JWT 토큰에서 직접 내 ID를 꺼내도록 수정!
      @Parameter(hidden = true) @AuthenticationPrincipal JwtPrincipal jwtPrincipal
  ) {
    Long userId = jwtPrincipal.getUserId();
    ProfileResponse response = userService.getMyProfile(userId);
    return ApiResponse.success(response);
  }

  @Operation(
      summary = "프로필 이미지 업로드용 Presigned URL 발급", 
      description = "프로필 이미지를 S3(MinIO)에 직접 업로드하기 위한 임시 URL과 Object Key를 발급받습니다."
  )
  @GetMapping("/image/presigned-url")
  public ApiResponse<Map<String, String>> getProfilePresignedUrl(
      @Parameter(hidden = true) @AuthenticationPrincipal JwtPrincipal jwtPrincipal,
      @Parameter(description = "업로드할 이미지 확장자 (예: .png, .jpg)", example = ".png") 
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

  @Operation(
      summary = "프로필 이미지 반영 (DB 업데이트)", 
      description = "Presigned URL로 이미지 업로드를 완료한 후, 발급받았던 objectKey를 서버에 전달하여 프로필을 최종 업데이트합니다."
  )
  @PatchMapping("/image")
  public ApiResponse<ProfileResponse> updateProfileImage(
      @Parameter(hidden = true) @AuthenticationPrincipal JwtPrincipal jwtPrincipal,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
          description = "발급받았던 objectKey 값",
          content = @Content(schema = @Schema(example = "{\"objectKey\": \"images/profile/1/profile.png\"}"))
      ) 
      @RequestBody Map<String, String> request 
  ) {
      Long userId = jwtPrincipal.getUserId();
      String objectKey = request.get("objectKey");
      
      ProfileResponse response = userService.updateProfileImage(userId, objectKey);      
      
      return ApiResponse.success(response);
  }
}