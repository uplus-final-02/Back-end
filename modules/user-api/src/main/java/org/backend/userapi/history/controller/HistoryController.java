package org.backend.userapi.history.controller;

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
        @AuthenticationPrincipal Long userId,
        @PathVariable Long videoId,
        @RequestBody @Valid SavePointRequest request
    ) {
        SavePointResponse response = historyService.savePoint(userId, videoId, request);
        return ApiResponse.success(response);
    }
}