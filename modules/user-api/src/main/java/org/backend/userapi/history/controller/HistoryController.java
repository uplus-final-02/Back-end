package org.backend.userapi.history.controller;

import core.security.principal.JwtPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.backend.userapi.common.dto.ApiResponse;
import org.backend.userapi.history.dto.SavePointRequest;
import org.backend.userapi.history.dto.SavePointResponse;
import org.backend.userapi.history.service.HistoryService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "시청 이력 API", description = "에피소드 재생 위치 저장 (이어보기 지원)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/histories")
public class HistoryController {

    private final HistoryService historyService;

    @Operation(summary = "재생 위치 저장", description = "현재 재생 위치(초)와 시청 상태(STARTED/WATCHING/COMPLETED)를 저장합니다. 이어보기에 사용됩니다.")
    @PostMapping("/savepoint/{videoId}")
    public ApiResponse<SavePointResponse> savePoint(
        @AuthenticationPrincipal JwtPrincipal jwtPrincipal,
        @PathVariable Long videoId,
        @RequestBody @Valid SavePointRequest request
    ) {
        SavePointResponse response = historyService.savePoint(jwtPrincipal.getUserId(), videoId, request);
        return new ApiResponse<>(200, "시청 기록 저장 성공", response);
    }
}