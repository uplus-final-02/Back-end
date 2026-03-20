package org.backend.userapi.user.dto.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WatchStatisticsResponse {
  private Integer totalWatchedCount;
  private Integer totalWatchTime;
  private List<GenreStatisticsResponse> statisticsByGenre;
  private String updatedAt;
}