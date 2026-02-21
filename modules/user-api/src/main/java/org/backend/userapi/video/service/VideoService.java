package org.backend.userapi.video.service;

import common.entity.Tag;
import common.enums.ContentType;
import content.entity.Content;
import content.entity.Video;
import content.entity.VideoFile;
import content.entity.WatchHistory;
import content.repository.ContentTagRepository;
import content.repository.VideoFileRepository;
import content.repository.VideoRepository;
import content.repository.WatchHistoryRepository;
import core.security.principal.JwtPrincipal;
import interaction.repository.BookmarkRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.backend.userapi.video.dto.VideoPlayDto;
import org.backend.userapi.video.dto.VideoResponseDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import user.entity.User;
import user.repository.UserRepository;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VideoService {

    private final VideoRepository videoRepository;
    private final VideoFileRepository videoFileRepository;
    private final WatchHistoryRepository watchHistoryRepository;
    private final UserRepository userRepository;
    private final BookmarkRepository bookmarkRepository;
    private final ContentTagRepository contentTagRepository;

    public VideoResponseDto getPlayInfo(Long videoId, JwtPrincipal jwtPrincipal) {
        // videos, contents 기본 정보 선조회
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new EntityNotFoundException("비디오 정보를 찾을 수 없습니다."));
        Content content = video.getContent();

        Long userId = jwtPrincipal.getUserId();
        // TODO : jwtFilter 완료되면 주석 해제 및 수정 필요
//        boolean isPaid = jwtPrincipal.getPaid();
//        boolean isUplus = jwtPrincipal.getUplus();
        Long contentId = content.getId();

        // TODO : jwtFilter 완료되면 주석 해제 및 수정 필요
        // 1. 영상 접근 권한 - 유저 권한 확인
        // 조건 : UserDetails.getPaid(), UserDetails.getUplus()
        // checkAccessPermission(content, user); // CustomUserDetails 필요

        // 2. 비디오 및 파일 기본 정보 조회 로직 구현
        // 동영상 파일의 HLS URL과 전체 재생 시간을 가져옵니다
        VideoFile videoFile = videoFileRepository.findByVideoId(videoId)
                .orElseThrow(() -> new EntityNotFoundException("재생 가능한 영상 파일이 없습니다."));
        String hlsUrl = videoFile.getHlsUrl();
        // int totalDuration = videoFile.getDurationSec(); // 현재 사용하는 곳이 없음

        // 3. 사용자 이어보기(History) 정보 연동
        // 최근 3개월 이력 중 마지막 시청 지점을 가져옵니다 (없으면 0초)
        Optional<WatchHistory> history = watchHistoryRepository.findByUserIdAndVideoId(userId, videoId);

        VideoPlayDto.PlaybackState playbackState = VideoPlayDto.PlaybackState.builder()
                .startPositionSec(history.map(WatchHistory::getLastPositionSec).orElse(0).longValue())
                .lastUpdated(history.map(h -> h.getUpdatedAt().toString()).orElse(null))
                .build();

        // 4. 이전/다음 에피소드 ID 계산 로직 구현
        Long nextId = videoRepository.findByContentIdAndEpisodeNo(video.getContent().getId(), video.getEpisodeNo() + 1)
                .map(Video::getId).orElse(null);
        Long prevId = videoRepository.findByContentIdAndEpisodeNo(video.getContent().getId(), video.getEpisodeNo() - 1)
                .map(Video::getId).orElse(null);

        // 5. 업로더 정보 조회
        String uploaderNickname = "Unknown";
        if (content.getUploaderId() != null) {
            uploaderNickname = userRepository.findById(content.getUploaderId())
                    .map(User::getNickname)
                    .orElse("Unknown");
        }

        // 6. 북마크 여부 조회
        boolean isBookmarked = bookmarkRepository.existsByUserIdAndContentId(userId, content.getId());

        // 최종 DTO 조립 및 반환
        VideoPlayDto videoPlayDto = VideoPlayDto.builder()
                .videoId(video.getId())
                .title(video.getTitle())
                .description(video.getDescription())
                .viewCount(video.getViewCount())
                .durationSec((long) videoFile.getDurationSec())
                .createdAt(video.getCreatedAt())
                .status(video.getStatus())
                .tags(contentTagRepository.findTagNamesByContentId(contentId).stream().toList())
                .uploaderType(content.getUploaderId() == null ? "ADMIN" : "USER")
                .uploaderNickname(uploaderNickname)
                .url(hlsUrl)
                .IsBookmarked(isBookmarked)
                .playbackState(playbackState)
                .context(VideoPlayDto.Context.builder()
                        .isSeries(content.getType() == ContentType.SERIES)
                        .contentsId(content.getId())
                        .episodeNumber(video.getEpisodeNo())
                        .nextVideoId(nextId)
                        .prevVideoId(prevId)
                        .build())
                .build();

        VideoResponseDto response = new VideoResponseDto();
        response.setCode(200);
        response.setMessage("재생 정보 조회 성공");
        response.setData(videoPlayDto);

        return response;

    }
    // access 검증 메소드
//  private void checkAccessPermission(Content content, CustomUserDetails user) {
//    String requiredLevel = content.getAccessLevel(); // FREE, BASIC, UPLUS
//
//    // 1. 무료 콘텐츠는 누구나 접근 가능
//    if ("FREE".equalsIgnoreCase(requiredLevel)) {
//      return;
//    }
//
//    // 유료인데 비로그인이면 거부
//    if (user == null) {
//      throw new AccessDeniedException("로그인이 필요합니다.");
//    }
//
//    boolean isPaid = user.getPaid();   // JWT payload에서 가져온 값
//    boolean isUplus = user.getUplus(); // JWT payload에서 가져온 값
//
//    // 2. 최소 등급이 BASIC인 경우 (유료 구독자 OR 유플러스 회원)
//    if ("BASIC".equalsIgnoreCase(requiredLevel)) {
//      if (!isPaid && !isUplus) {
//        throw new AccessDeniedException("구독권이 필요합니다.");
//      }
//    }
//    // 3. 최소 등급이 UPLUS인 경우 (오직 유플러스 회원만)
//    else if ("UPLUS".equalsIgnoreCase(requiredLevel)) {
//      if (!isUplus) {
//        throw new AccessDeniedException("U+ 회원 전용 콘텐츠입니다.");
//      }
//    }
//  }
}