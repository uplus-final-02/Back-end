package org.backend.userapi.video.service;

import common.entity.Tag;
import common.enums.ContentType;
import common.enums.HistoryStatus;
import content.entity.Content;
import content.entity.Video;
import content.entity.VideoFile;
import content.entity.WatchHistory;
import content.repository.ContentTagRepository;
import content.repository.VideoFileRepository;
import content.repository.VideoRepository;
import content.repository.WatchHistoryRepository;
import core.security.principal.JwtPrincipal;
import core.storage.service.HlsUrlProvider;
import interaction.repository.BookmarkRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import org.backend.userapi.common.exception.VideoNotFoundException;
import org.backend.userapi.video.dto.VideoPlayDto;
import org.backend.userapi.video.dto.VideoSimpleMetaData;
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
@Transactional
public class VideoService {

    private final VideoRepository videoRepository;
    private final VideoFileRepository videoFileRepository;
    private final WatchHistoryRepository watchHistoryRepository;
    private final UserRepository userRepository;
    private final BookmarkRepository bookmarkRepository;
    private final ContentTagRepository contentTagRepository;
    private final HlsUrlProvider hlsUrlProvider;

    public VideoPlayDto getPlayInfo(Long videoId, JwtPrincipal jwtPrincipal) {
        // videos, contents 기본 정보 선조회
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new VideoNotFoundException("비디오 정보를 찾을 수 없습니다."));
        Content content = video.getContent();

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
        Long contentId = content.getId();

        // 2. 비디오 및 파일 기본 정보 조회 로직 구현
        VideoFile videoFile = videoFileRepository.findByVideoId(videoId).orElse(null);

        // HlsUrlProvider를 통해 URL 생성 (Local or CloudFront)
        String hlsUrl = (videoFile != null) ? hlsUrlProvider.getHlsUrl(videoFile.getId()) : null;

        long durationSec = (videoFile != null) ? videoFile.getDurationSec() : 0;

        // 3. 사용자 이어보기(History) 정보 연동 (없을 경우 insert)
        Optional<WatchHistory> history = watchHistoryRepository.findByUserIdAndVideoId(userId, videoId);

        if (history.isPresent()) {
            VideoPlayDto.PlaybackState playbackState = VideoPlayDto.PlaybackState.builder()
                    .startPositionSec(history.map(WatchHistory::getLastPositionSec).orElse(0).longValue())
                    .lastUpdated(history.map(h -> h.getUpdatedAt().toString()).orElse(null))
                    .build();
        } else { // 시청 이력이 없을 경우 insert
            WatchHistory newHistory = WatchHistory.builder()
                    .userId(userId)
                    .video(video)
                    .status(HistoryStatus.STARTED)
                    .build();

            watchHistoryRepository.save(newHistory);
        }

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
}