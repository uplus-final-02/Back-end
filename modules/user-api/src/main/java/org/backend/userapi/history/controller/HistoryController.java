package org.backend.userapi.history.controller;

import core.security.principal.JwtPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.backend.userapi.common.dto.ApiResponse;
import org.backend.userapi.history.dto.SavePointRequest;
import org.backend.userapi.history.dto.SavePointResponse;
import org.backend.userapi.history.service.HistoryService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/histories")
public class HistoryController {

    private final HistoryService historyService;

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