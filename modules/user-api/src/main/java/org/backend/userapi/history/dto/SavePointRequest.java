package org.backend.userapi.history.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SavePointRequest {
    private Integer positionSec;
    private Integer playDurationSec;
}
