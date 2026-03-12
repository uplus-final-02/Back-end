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
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import org.backend.userapi.common.exception.VideoNotFoundException;
import org.backend.userapi.video.dto.VideoPlayDto;
import org.backend.userapi.video.dto.VideoSimpleMetaData;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
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

    public VideoPlayDto getPlayInfo(Long videoId, JwtPrincipal jwtPrincipal) {
        // videos, contents 기본 정보 선조회
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new VideoNotFoundException("비디오 정보를 찾을 수 없습니다."));
        Content content = video.getContent();

        // [인가] 접근 권한 판별
        checkAccessPermission(content, jwtPrincipal);
        
        // contents.type 에 따른 기본 정보 조회 분기 처리 (title, description, thumbnail_url)
        String title = null;
        String description = null;
        String thumbnailUrl = null;
//        String thumbnailPrefix = "https://image.tmdb.org/t/p/w1280";
        if (content.getType() == ContentType.SINGLE) {
            title = content.getTitle();
            String rawJson = content.getDescription();
            if (rawJson != null && !rawJson.isEmpty()) {
                ObjectMapper mapper = new ObjectMapper();
                try {
                    JsonNode rootNode = mapper.readTree(rawJson);

                    // "summary" 키가 존재하는지 확인 후 가져오기 (없으면 null 반환)
                    if (rootNode.has("summary") && !rootNode.get("summary").isNull()) {
                        description = rootNode.get("summary").asString();
                    }
                } catch (Exception e) {
                    // JSON 파싱 에러 처리
                    e.printStackTrace();
                }
            }
            thumbnailUrl = content.getThumbnailUrl();
        } else {
            title = video.getTitle();
            description = video.getDescription();
            thumbnailUrl = video.getThumbnailUrl();
        }

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
        VideoFile videoFile = videoFileRepository.findByVideoId(videoId).orElse(null);
        String hlsUrl = (videoFile != null) ? videoFile.getHlsUrl() : null;
        long durationSec = (videoFile != null) ? videoFile.getDurationSec() : 0;
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

        return VideoPlayDto.builder()
                .videoId(video.getId())
                .title(title)
                .description(description)
//                .thumbnailUrl(thumbnailPrefix + thumbnailUrl)
                .thumbnailUrl(thumbnailUrl)
                .viewCount(video.getViewCount())
                .durationSec(durationSec)
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

    }

    @Transactional(readOnly = true)
    public VideoSimpleMetaData getContentIdByVideoId(Long videoId) {
        Tuple tuple = videoRepository.findMetaDataById(videoId)
                .orElseThrow(() -> new VideoNotFoundException("비디오 정보를 찾을 수 없습니다."));

        return new VideoSimpleMetaData(
                tuple.get("contentId", Long.class),
                tuple.get("durationSec", Integer.class)
        );
    }

    // access 검증 메소드
  private void checkAccessPermission(Content content, JwtPrincipal jwtPrincipal) {
	  
    String requiredLevel = content.getAccessLevel().name(); // FREE, BASIC, UPLUS

    
    // 1. 무료 콘텐츠는 누구나 접근 가능
    if ("FREE".equalsIgnoreCase(requiredLevel)) {
      return;
    }

    // 유료인데 비로그인이면 거부
//    if (jwtPrincipal == null) {
//        throw new AccessDeniedException("로그인이 필요합니다.");
//    }
    
    boolean isPaid = jwtPrincipal.isPaid();
    boolean isUplus = jwtPrincipal.isUplus();

    // 2. 베이직 콘텐츠는 유료 구독자 또는 U+ 회원 접근 가능
    if ("BASIC".equalsIgnoreCase(requiredLevel)) {
        if (!isPaid && !isUplus) {
            throw new AccessDeniedException("베이직 구독 또는 LG U+ 회원 인증이 필요합니다.");
        }
        return;
    }

    // 3. 최소 등급이 UPLUS인 경우 (오직 유플러스 회원만)
    if ("UPLUS".equalsIgnoreCase(requiredLevel)) {
        if (!isUplus) {
            throw new AccessDeniedException("LG U+ 회원 전용 콘텐츠입니다.");
        }
        return;
    }
    
    // 알 수 없는 접근 레벨 방어
    throw new IllegalStateException("지원하지 않는 접근 레벨입니다: " + requiredLevel);
  }
}