package org.backend.userapi.user.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenreStatisticsResponse {
  private Long tagId;
  private String tagName;
  private Integer watchedCount;
  private Integer watchTime;
  private Double percentage;
}