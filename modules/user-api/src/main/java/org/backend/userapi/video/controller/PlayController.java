package org.backend.userapi.video.controller;

import lombok.RequiredArgsConstructor;
import org.backend.userapi.video.dto.VideoResponseDto;
import org.backend.userapi.video.service.VideoService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
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
       @AuthenticationPrincipal UserDetails user // (1) JWT에서 파싱된 유저 정보 주입 (***UserDetails가 jwt 쪽에서 생성되었다고 가정*** ) // TODO : jwt의 정보를 전달받는 방식에 따라 수정 필요
  ) {
    // user가 null이면 비로그인 상태 (Security 설정에 따라 필터에서 걸러지거나 여기로 도달)
    // TODO : 도달하지 않는 로직이라면 삭제 요망
    // Long userId = (user != null) ? user.getUserId() : null;

    // 서비스 호출
    return videoService.getPlayInfo(videoId, user);
  }
}