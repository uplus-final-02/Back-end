package org.backend.userapi.video.controller;

import core.security.principal.JwtPrincipal;
import lombok.RequiredArgsConstructor;
import org.backend.userapi.common.dto.ApiResponse;
import org.backend.userapi.video.dto.VideoPlayDto;
import org.backend.userapi.video.dto.VideoResponseDto;
import org.backend.userapi.video.service.VideoService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/contents")
@RequiredArgsConstructor
public class PlayController {

    private final VideoService videoService;

    @GetMapping("/{videoId}/play")
    public ApiResponse<VideoPlayDto> playVideo(
            @PathVariable Long videoId,
            @AuthenticationPrincipal JwtPrincipal jwtPrincipal
    ) {
        VideoPlayDto response = videoService.getPlayInfo(videoId, jwtPrincipal);
        return new ApiResponse<>(200, "재생 정보 조회 성공", response);
    }
}