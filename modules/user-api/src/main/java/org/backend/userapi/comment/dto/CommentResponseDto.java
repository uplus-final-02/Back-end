package org.backend.userapi.comment.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentResponseDto {
  private Long commentId;
  private String body;
  private LocalDateTime createdAt;

  // 유저 정보
  private Long userId;
  private String nickname;
  private String profileImageUrl;
}
