package org.backend.video.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class VideoDto {

  // [1] 기본 식별자
  private Long videoId;
  private String title;
  private String description;

  // [2] 재생 URL (Source 객체 대신 단순 문자열)
  private String url;

  // [3] 이어보기 상태
  private PlaybackState playbackState;

  // [4] 시리즈 정보
  private Context context;

  // --- Inner Classes ---

  @Getter
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  public static class PlaybackState {

    private long startPositionSec;
    private String lastUpdated;
  }

  @Getter
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  public static class Context {

    private boolean isSeries;
    private Long contentsId;
    private Integer episodeNumber;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long nextVideoId;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long prevVideoId;
  }
}