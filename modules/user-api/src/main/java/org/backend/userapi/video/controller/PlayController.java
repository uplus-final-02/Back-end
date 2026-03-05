package org.backend.userapi.video.controller;

import core.security.principal.JwtPrincipal;
import lombok.RequiredArgsConstructor;
import org.backend.userapi.common.dto.ApiResponse;
import org.backend.userapi.video.dto.VideoPlayDto;
import org.backend.userapi.video.dto.VideoSimpleMetaData;
import org.backend.userapi.video.service.VideoService;
import org.backend.userapi.video.service.ViewCountService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/contents")
@RequiredArgsConstructor
public class PlayController {

    private final VideoService videoService;
    private final ViewCountService viewCountService;

    @GetMapping("/{videoId}/play")
    public ApiResponse<VideoPlayDto> playVideo(
            @PathVariable Long videoId,
            @AuthenticationPrincipal JwtPrincipal jwtPrincipal
    ) {
        VideoPlayDto response = videoService.getPlayInfo(videoId, jwtPrincipal);
        return new ApiResponse<>(200, response.getStatusDescription(), response);
    }

    @PostMapping("/{videoId}/views")
    public ApiResponse<Void> increaseViewCount(
            @PathVariable Long videoId,
            @AuthenticationPrincipal JwtPrincipal jwtPrincipal
    ) {
        VideoSimpleMetaData metaData = videoService.getContentIdByVideoId(videoId);
        viewCountService.incrementViewCount(
                metaData.getContentId(), videoId, jwtPrincipal.getUserId(), metaData.getDurationSec()
        );
        return new ApiResponse<>(200, "조회수 증가 처리 접수", null);
    }
}