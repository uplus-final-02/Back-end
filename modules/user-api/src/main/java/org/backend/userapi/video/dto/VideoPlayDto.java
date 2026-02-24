package org.backend.userapi.video.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import common.entity.Tag;
import common.enums.ContentStatus;
import common.enums.VideoStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.backend.userapi.content.dto.ContentDetailResponse;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class VideoPlayDto {

    // [1] 영상 기본 메타데이터
    private Long videoId;
    private String title;
    private String description;

    private Long viewCount;
    private Long durationSec;
    private LocalDateTime createdAt;
    private VideoStatus status;

    private List<String> tags;

    // [2] 업로더 정보
    private String uploaderType;
    private String uploaderNickname;

    // [3] 재생 URL (Source 객체 대신 단순 문자열)
    private String url;

    // [4] 이어보기 상태 (개인화 데이터)
    private boolean IsBookmarked;
    private PlaybackState playbackState;

    // [5] 시리즈 연동 정보
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