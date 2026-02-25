package org.backend.userapi.content.service;

import common.entity.Tag;
import common.enums.ContentStatus;
import common.enums.ContentType;
import content.entity.Content;
import content.entity.Video;
import content.entity.VideoFile;
import content.entity.WatchHistory;
import content.repository.ContentRepository;
import content.repository.VideoRepository;
import content.repository.WatchHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.backend.userapi.content.dto.DefaultContentResponse;
import org.backend.userapi.common.exception.ContentNotFoundException;
import org.backend.userapi.content.dto.ContentDetailResponse;
import org.backend.userapi.content.dto.EpisodeResponse;
import org.backend.userapi.content.dto.EpisodesResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import user.entity.User;
import user.repository.UserNicknameInfo;
import user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContentService {

    private final ContentRepository contentRepository;
    private final WatchHistoryRepository watchHistoryRepository;
    private final UserRepository userRepository;
    private final VideoRepository videoRepository;

    public List<DefaultContentResponse> getWatchingContents(Long userId) {
        // 1. 최근 3개월 이내 기록 조회 (Content까지 한 번에 조인되어 옴)
        LocalDateTime threeMonthsAgo = LocalDateTime.now().minusMonths(3);

        // 최근 3개월 시청이력 중, (중복 제거를 고려해) 넉넉하게 50개 조회
        List<WatchHistory> histories = watchHistoryRepository.findRecentWatchHistories(
                userId,
                threeMonthsAgo,
                PageRequest.of(0, 50)
        );

        // 2. contentId 기준으로 중복 제거 (LinkedHashMap으로 순서 보장: 최신순)
        // (같은 작품의 1화, 2화를 봤다면 가장 최근인 2화만 남김)
        Map<Long, WatchHistory> distinctHistoryMap = new LinkedHashMap<>();
        for (WatchHistory wh : histories) {
            Long contentId = wh.getVideo().getContent().getId();

            // 맵에 없으면 추가 (이미 있으면 더 최신 기록이 들어간 것이므로 패스)
            distinctHistoryMap.putIfAbsent(contentId, wh);

            if (distinctHistoryMap.size() == 5) break; // 5개 채워지면 중단
        }

        if (distinctHistoryMap.isEmpty()) {
            return Collections.emptyList();
        }

        // 3. Content 추출
        List<Content> contents = distinctHistoryMap.values().stream()
                .map(wh -> wh.getVideo().getContent())
                .collect(Collectors.toList());

        // 4. 변환 로직 호출
        return convertToDefaultContentResponses(contents);
    }

    public List<DefaultContentResponse> getDefaultContents(String uploaderType, String tag, Pageable pageable) {

        // 1. DB 레벨 필터링: 'ACTIVE' 상태, 제공자, 태그 조건이 모두 적용된 데이터 조회 (N+1 방지)
        Slice<Content> contentSlice = contentRepository.findContentsWithFilters(
                ContentStatus.ACTIVE, // 🔥 상태 파라미터 명시적 전달
                uploaderType,
                tag,
                pageable
        );

        List<Content> contents = contentSlice.getContent();

        // 2. 변환 로직 호출
        return convertToDefaultContentResponses(contents);
    }

    /**
     * Content 리스트를 DefaultContentResponse 리스트로 변환 (닉네임 조회 포함)
     */
    public List<DefaultContentResponse> convertToDefaultContentResponses(List<Content> contents) {
        if (contents.isEmpty()) {
            return Collections.emptyList();
        }

        // 1. User 닉네임 일괄 조회
        Map<Long, String> uploaderNicknameMap = getUploaderNicknameMap(contents);

        // 2. DTO 변환
        return contents.stream()
                .map(content -> convertToDto(content, uploaderNicknameMap))
                .collect(Collectors.toList());
    }

    // ---------------------- [Private Helper Methods] ----------------------

    /**
     * 콘텐츠 리스트에서 업로더 ID를 추출하여 닉네임 맵(Map)으로 반환
     */
    private Map<Long, String> getUploaderNicknameMap(List<Content> contents) {
        Set<Long> uploaderIds = contents.stream()
                .map(Content::getUploaderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (uploaderIds.isEmpty()) {
            return Collections.emptyMap();
        }

        // IN 쿼리로 유저를 일괄 조회한 뒤 바로 Map으로 수집
        List<UserNicknameInfo> results = userRepository.findNicknamesByIds(uploaderIds);
        
        return results.stream()
                .collect(Collectors.toMap(
                        UserNicknameInfo::getId,
                        UserNicknameInfo::getNickname
                ));
    }

    /**
     * Content 엔티티를 DefaultContentResponse DTO로 변환
     */
    private DefaultContentResponse convertToDto(Content content, Map<Long, String> uploaderNicknameMap) {
        // 업로더 이름 결정
        String uploaderName = (content.getUploaderId() == null)
                ? "관리자"
                : uploaderNicknameMap.getOrDefault(content.getUploaderId(), "알 수 없음");

        // 활성 상태인 태그만 필터링
        List<Tag> activeTags = content.getTags().stream()
                .filter(Tag::getIsActive)
                .collect(Collectors.toList());

        // 오버로딩된 from 메소드 사용 (필터링된 태그 전달)
        return DefaultContentResponse.from(content, uploaderName, activeTags);
    }

    public ContentDetailResponse getContentDetail(Long contentId) {
        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new ContentNotFoundException(
                        "콘텐츠를 찾을 수 없습니다. contentId=" + contentId
                ));

        return ContentDetailResponse.builder()
                .contentId(content.getId())
                .type(content.getType())
                .title(content.getTitle())
                .description(content.getDescription())
                .thumbnailUrl(content.getThumbnailUrl())
                .status(content.getStatus())
                .totalViewCount(content.getTotalViewCount())
                .bookmarkCount(content.getBookmarkCount())
                .uploaderId(content.getUploaderId())
                .accessLevel(content.getAccessLevel())
                .createdAt(content.getCreatedAt())
                .updatedAt(content.getUpdatedAt())
                .tags(content.getTags().stream()
                        .map(ContentDetailResponse.TagResponse::from)
                        .toList()
                )
                .build();
    }

    public EpisodesResponse getContentEpisodes(Long contentId) {
        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new ContentNotFoundException(
                        "콘텐츠를 찾을 수 없습니다. contentId=" + contentId
                ));

        if (content.getType() != ContentType.SERIES) {
            throw new IllegalArgumentException(
                    "시리즈 콘텐츠만 에피소드 목록을 조회할 수 있습니다. contentId=" + contentId
            );
        }

        List<Video> videos = videoRepository.findEpisodesWithVideoFileByContentId(contentId);

        List<EpisodeResponse> episodes = videos.stream()
                .map(v -> {
                    VideoFile vf = v.getVideoFile();
                    Integer durationSec = (vf != null) ? vf.getDurationSec() : null;

                    return EpisodeResponse.builder()
                            .videoId(v.getId())
                            .episodeNo(v.getEpisodeNo())
                            .title(v.getTitle())
                            .description(v.getDescription())
                            .thumbnailUrl(v.getThumbnailUrl())
                            .viewCount(v.getViewCount())
                            .status(v.getStatus())
                            .durationSec(durationSec)
                            .build();
                })
                .toList();

        return EpisodesResponse.of(contentId, episodes);
    }
}