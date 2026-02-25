package org.backend.userapi.user.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WatchHistoryDto {
  private Long historyId;
  private Long contentId;
  private Long episodeId;
  private String title;
  private String episodeTitle;
  private Integer episodeNumber;
  private String thumbnailUrl;
  private String contentType;
  private String category;
  private Integer lastPosition;
  private Integer duration;
  private Integer progressPercent;
  private String status;
  private LocalDateTime watchedAt;
  private LocalDateTime deletedAt;
}
