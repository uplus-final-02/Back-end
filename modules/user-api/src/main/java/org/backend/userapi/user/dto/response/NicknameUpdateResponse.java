package org.backend.userapi.user.dto.response;

import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;

@Getter
@Builder
public class NicknameUpdateResponse {
  private String nickname;
  private LocalDateTime nextChangeAvailableAt; // 다음 변경 가능 일시
}