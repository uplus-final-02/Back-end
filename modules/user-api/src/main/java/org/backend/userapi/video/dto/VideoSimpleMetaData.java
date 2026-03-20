package org.backend.userapi.video.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class VideoSimpleMetaData {
    private Long contentId;
    private Integer durationSec;
}
