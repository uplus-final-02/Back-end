package org.backend.userapi.video.controller;

import core.security.principal.JwtPrincipal;
import lombok.RequiredArgsConstructor;
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
  public VideoResponseDto playVideo(
      @PathVariable Long videoId,
      @AuthenticationPrincipal JwtPrincipal jwtPrincipal
  ) {
    return videoService.getPlayInfo(videoId, jwtPrincipal);
  }
}