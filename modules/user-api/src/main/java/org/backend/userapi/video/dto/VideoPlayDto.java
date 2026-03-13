package org.backend.userapi.video.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import common.entity.Tag;
import common.enums.ContentStatus;
import common.enums.VideoStatus;
import lombok.*;
import org.backend.userapi.content.dto.ContentDetailResponse;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class VideoPlayDto {

    // [1] 영상 기본 메타데이터
    private Long videoId;
    private String title;
    private String description;
    private String thumbnailUrl;

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

    // cookie 에 넣을 정보들
    private String policy;
    private String signature;
    private String keyPairId;

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

    // 메시지를 동적으로 생성하는 편의 메서드
    public String getStatusDescription() {
        if (this.url == null) {
            return "현재 재생 가능한 영상이 없습니다.";
        }
        return "재생 정보 조회 성공";
    }
}