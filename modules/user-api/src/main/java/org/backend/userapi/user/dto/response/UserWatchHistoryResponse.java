package org.backend.userapi.user.dto.response;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserWatchHistoryResponse {
  private Long historyId;
  private Long userContentId;
  private Long parentContentId;
  private String title;
  private String description;
  private String thumbnailUrl;
  private String contentStatus;
  private LocalDateTime lastWatchedAt;
  private LocalDateTime deletedAt;
}