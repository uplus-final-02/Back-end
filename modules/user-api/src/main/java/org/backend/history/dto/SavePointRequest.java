package org.backend.history.dto;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "시청 기록 저장 요청 DTO")
public class SavePointRequest {

  @NotNull
  @Min(0)
  @Schema(description = "현재 재생 위치 (초)", example = "125")
  private Integer positionSec;

  @NotNull
  @Min(0)
  @Schema(description = "이번 세션에서의 시청 지속 시간 (초)", example = "65",
      description = "이 값이 60초 이상일 때 상태가 WATCHING으로 변경됩니다.")
  private Integer playDurationSec;
}