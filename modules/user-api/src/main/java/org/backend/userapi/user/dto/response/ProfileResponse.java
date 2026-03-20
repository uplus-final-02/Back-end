package org.backend.userapi.user.dto.response;

import lombok.Builder;
import lombok.Getter;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "사용자 프로필 상세 정보 응답 데이터")
@Getter
@Builder
public class ProfileResponse {
  
  @Schema(description = "사용자 고유 ID", example = "1")
  private Long userId;
  
  @Schema(description = "로그인 이메일 (소셜/일반)", example = "user@example.com", nullable = true)
  private String email;
  
  @Schema(description = "사용자 닉네임", example = "몽향")
  private String nickname;
  
  @Schema(description = "프로필 이미지 전체 URL (S3/MinIO 주소)", example = "https://minio.example.com/bucket/images/profile/1/profile.png", nullable = true)
  private String profileImageUrl;
  
  @Schema(description = "구독 상태 (SUBSCRIBED: 구독중, NONE: 미구독)", example = "SUBSCRIBED")
  private String subscriptionStatus; // "SUBSCRIBED" 또는 "NONE"
  
  @Schema(description = "U+ 인증 회원 여부", example = "true")
  private Boolean isUPlusMember;
  
  @Schema(description = "사용자가 선택한 선호 태그 목록")
  private List<TagDto> preferredTags;
  
  @Schema(description = "가입 일시", example = "2023-10-25T10:30:00")
  private LocalDateTime createdAt;
  
  @Schema(description = "마지막 닉네임 변경 일시", example = "2024-02-15T14:20:00")
  private LocalDateTime lastNicknameChangedAt;

  @Schema(description = "선호 태그 상세 정보")
  @Getter
  @Builder
  public static class TagDto {
    @Schema(description = "태그 ID", example = "5")
    private Long tagId;
    
    @Schema(description = "태그명", example = "액션")
    private String name;
  }
}