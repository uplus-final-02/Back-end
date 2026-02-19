package org.backend.userapi.user.dto.response;

import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class ProfileResponse {
  private Long userId;
  private String email;
  private String nickname;
  private String profileImageUrl;
  private String subscriptionStatus; // "SUBSCRIBED" 또는 "NONE"
  private Boolean isUPlusMember;
  private List<TagDto> preferredTags;
  private LocalDateTime createdAt;
  private LocalDateTime lastNicknameChangedAt;

  @Getter
  @Builder
  public static class TagDto {
    private Long tagId;
    private String name;
  }
}