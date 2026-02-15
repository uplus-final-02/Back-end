package org.backend.history.controller;

import org.backend.history.dto.SavePointRequest;
import org.backend.history.dto.SavePointResponse;
import org.hibernate.annotations.Parameter;

@Tag(name = "History API", description = "시청 기록 및 이어보기 관련 API")
public interface HistoryApi {

  @Operation(summary = "시청 위치 저장 (Heartbeat)",
      description = "영상을 시청하는 동안 주기적(10초)으로 호출하여 재생 위치를 저장합니다.<br>" +
          "- 시청 지속 시간이 60초 이상이면 상태가 <b>WATCHING</b>으로 변경됩니다.<br>" +
          "- 전체 길이의 90% 이상 시청 시 상태가 <b>COMPLETED</b>로 변경됩니다.")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "저장 성공",
          content = @Content(schema = @Schema(implementation = SavePointResponse.class))),
      @ApiResponse(responseCode = "404", description = "존재하지 않는 비디오 ID", content = @Content),
      @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자", content = @Content)
  })
    // 반환 타입도 ResponseEntity로 감싸주면 더 명확합니다 (선택사항)
  SavePointResponse savePoint(
      @Parameter(hidden = true) // Swagger에서 숨김 처리
      Long userId,

      @Parameter(description = "비디오 ID", example = "1", required = true)
      Long videoId,

      @Parameter(description = "시청 위치 및 지속 시간 정보", required = true)
      SavePointRequest request
  );
}