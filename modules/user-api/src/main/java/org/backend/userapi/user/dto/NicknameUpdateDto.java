package org.backend.userapi.user.dto;

import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;

@Getter
@Builder
public class NicknameUpdateDto {
  private String nickname;
  private LocalDateTime nextChangeAvailableAt; // 다음 변경 가능 일시
}