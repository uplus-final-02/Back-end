package org.backend.userapi.content.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WatchingContentResponse {
    private Long contentId;
    private String title;
    private String thumbnailUrl;

    private Long lastVideoId;       // 마지막으로 본 에피소드 ID
    private String lastVideoTitle;  // 마지막으로 본 에피소드 제목 (단건의 경우는 null 가능)

    private Integer currentPositionSec; // 현재 재생 위치
    private LocalDateTime lastWatchedAt; // 마지막 시청 시각
}