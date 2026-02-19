package org.backend.userapi.content.dto;

import common.enums.VideoStatus;
import lombok.*;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EpisodeResponse {

    private Long videoId;
    private Integer episodeNo;

    private String title;
    private String description;
    private String thumbnailUrl;

    private Long viewCount;
    private VideoStatus status;

    private Integer durationSec;
}