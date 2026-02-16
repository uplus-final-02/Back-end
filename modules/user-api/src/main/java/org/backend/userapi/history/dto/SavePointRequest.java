package org.backend.userapi.history.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SavePointRequest {
    @NotNull(message = "재생 위치는 필수입니다.")
    @Min(value = 0, message = "재생 위치는 0초 이상이어야 합니다.")
    private Integer positionSec;

    @NotNull(message = "총 재생 시간은 필수입니다.")
    @Min(value = 0, message = "총 재생 시간은 0초 이상이어야 합니다.")
    private Integer playDurationSec;
}
