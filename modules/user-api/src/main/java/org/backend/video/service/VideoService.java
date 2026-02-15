package org.backend.video.service;

import content.entity.Content;
import content.entity.Video;
import content.entity.VideoFile;
import content.entity.WatchHistory;
import content.repository.ContentRepository;
import content.repository.VideoFileRepository;
import content.repository.VideoRepository;
import content.repository.WatchHistoryRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.backend.video.dto.VideoDto;
import org.backend.video.dto.VideoResponseDto;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class VideoService {

  private final ContentRepository contentRepository;
  private final VideoRepository videoRepository;
  private final VideoFileRepository videoFileRepository;
  private final WatchHistoryRepository watchHistoryRepository;

  public VideoResponseDto getPlayInfo(Long videoId, UserDetails user) {
    // videos, contents 기본 정보 선조회
    Video video = videoRepository.findById(videoId)
        .orElseThrow(() -> new EntityNotFoundException("비디오 정보를 찾을 수 없습니다."));
    Content content = video.getContentId();


    // 1. 영상 접근 권한 - 유저 권한 확인
    // UserDetails.getPaid(), UserDetails.getUplus()
    checkAccessPermission(content, user);

    // 2. 비디오 및 파일 기본 정보 조회 로직 구현
    // 동영상 파일의 HLS URL과 전체 재생 시간을 가져옵니다
    VideoFile videoFile = videoFileRepository.findByVideoId(videoId);
    if (videoFile == null) {
      throw new EntityNotFoundException("재생 가능한 영상 파일이 없습니다.");
    }
    String hlsUrl = videoFile.getHlsUrl();
    // int totalDuration = videoFile.getDurationSec(); // 현재 사용하는 곳이 없음

    // 3. 사용자 이어보기(History) 정보 연동
    // 최근 3개월 이력 중 마지막 시청 지점을 가져옵니다 (없으면 0초)
    WatchHistory history = watchHistoryRepository.findByUserIdAndVideoId(user.getSub(), videoId);

    VideoDto.PlaybackState playbackState = VideoDto.PlaybackState.builder()
        .startPositionSec(history.map(h -> (long)h.getLastPosition()).orElse(0L))
        .lastUpdated(history.map(h -> h.getUpdatedAt().toString()).orElse(null))
        .build();

    // 4. 이전/다음 에피소드 ID 계산 로직 구현
    Long nextId = videoRepository.findByContentIdAndEpisodeNo(video.getContentId(), video.getEpisodeNo() + 1)
        .map(Video::getId).orElse(null);
    Long prevId = videoRepository.findByContentIdAndEpisodeNo(video.getContentId(), video.getEpisodeNo() - 1)
        .map(Video::getId).orElse(null);

    // 최종 DTO 조립 및 반환
    return VideoDto.builder()
        .videoId(video.getId())
        .title(video.getTitle())
        .description(video.getDescription())
        .url(hlsUrl)
        .playbackState(playbackState)
        .context(VideoDto.Context.builder()
            .isSeries(video.getContentId() != null)
            .contentsId(video.getContentId())
            .episodeNumber(video.getEpisodeNo())
            .nextVideoId(nextId) // 다음 화 자동 재생
            .prevVideoId(prevId)
            .build())
        .build();


  }
  // access 검증 메소드
  private void checkAccessPermission(Content content, CustomUserDetails user) {
    String requiredLevel = content.getAccessLevel(); // FREE, BASIC, UPLUS

    // 1. 무료 콘텐츠는 누구나 접근 가능
    if ("FREE".equalsIgnoreCase(requiredLevel)) {
      return;
    }

    // 유료인데 비로그인이면 거부
    if (user == null) {
      throw new AccessDeniedException("로그인이 필요합니다.");
    }

    boolean isPaid = user.getPaid();   // JWT payload에서 가져온 값
    boolean isUplus = user.getUplus(); // JWT payload에서 가져온 값

    // 2. 최소 등급이 BASIC인 경우 (유료 구독자 OR 유플러스 회원)
    if ("BASIC".equalsIgnoreCase(requiredLevel)) {
      if (!isPaid && !isUplus) {
        throw new AccessDeniedException("구독권이 필요합니다.");
      }
    }
    // 3. 최소 등급이 UPLUS인 경우 (오직 유플러스 회원만)
    else if ("UPLUS".equalsIgnoreCase(requiredLevel)) {
      if (!isUplus) {
        throw new AccessDeniedException("U+ 회원 전용 콘텐츠입니다.");
      }
    }
  }
}