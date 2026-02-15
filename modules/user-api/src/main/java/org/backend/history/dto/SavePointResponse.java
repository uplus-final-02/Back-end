package org.backend.history.dto;

import common.enums.HistoryStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Schema(description = "시청 기록 저장 응답 DTO")
public class SavePointResponse {

  @Schema(description = "시청 기록 ID (PK)", example = "1052")
  private Long historyId;

  @Schema(description = "현재 시청 상태 (STARTED, WATCHING, COMPLETED)", example = "WATCHING")
  private HistoryStatus status;

  @Schema(description = "DB에 최종 저장된 시청 위치 (초)", example = "125")
  private Integer savedPositionSec;

  //@Schema(description = "다음 에피소드 ID (상태가 COMPLETED일 때만 값이 존재함)", example = "203", nullable = true)
  //private Long nextVideoId;
}