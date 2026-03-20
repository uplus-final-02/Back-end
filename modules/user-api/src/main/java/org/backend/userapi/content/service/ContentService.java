package org.backend.userapi.content.service;

import common.entity.Tag;
import common.enums.ContentAccessLevel;
import common.enums.ContentStatus;
import common.enums.ContentType;
import common.enums.HistoryStatus;
import content.entity.Content;
import content.entity.Video;
import content.entity.VideoFile;
import content.entity.WatchHistory;
import content.repository.ContentRepository;
import content.repository.ContentTagRepository;
import content.repository.VideoRepository;
import content.repository.WatchHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.backend.userapi.content.dto.DefaultContentResponse;
import org.backend.userapi.common.exception.ContentNotFoundException;
import org.backend.userapi.content.dto.ContentDetailResponse;
import org.backend.userapi.content.dto.EpisodeResponse;
import org.backend.userapi.content.dto.EpisodesResponse;
import org.backend.userapi.user.dto.response.WatchHistoryListResponse;
import org.backend.userapi.user.dto.response.WatchHistoryResponse;
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
    private final ContentTagRepository contentTagRepository;

    public WatchHistoryListResponse getWatchingContents(Long userId) {
        // 1. 최근 3개월 이내 기록 중 ACTIVE 콘텐츠만, contentId당 가장 최신의 기록 1개씩 총 5개 조회
        LocalDateTime threeMonthsAgo = LocalDateTime.now().minusMonths(3);

        List<WatchHistory> histories = watchHistoryRepository.findRecentActiveWatchHistories(
                userId,
                threeMonthsAgo,
                ContentStatus.ACTIVE,
                PageRequest.of(0, 5)
        );

        if (histories.isEmpty()) {
            return WatchHistoryListResponse.builder()
                    .watchHistory(Collections.emptyList())
                    .hasNext(false)
                    .nextCursor(null)
                    .build();
        }

        List<Long> contentIds = histories.stream()
                .map(WatchHistory::getContentId)
                .distinct()
                .collect(Collectors.toList());

        // 복수 태그 매핑 (N+1 방지)
        Map<Long, List<String>> tagMap = new HashMap<>();
        if (!contentIds.isEmpty()) {
            List<Object[]> tagResults = contentTagRepository.findTagNamesByContentIds(contentIds);
            for (Object[] result : tagResults) {
                Long cId = (Long) result[0]; // content.id
                String tagName = (String) result[1]; // t.name

                tagMap.computeIfAbsent(cId, k -> new ArrayList<>()).add(tagName);
            }
        }

        List<WatchHistoryResponse> dtoList = histories.stream().map(history -> {
            // 영상 길이
            int duration = 0;
            if (history.getVideo().getVideoFile() != null) {
                duration = history.getVideo().getVideoFile().getDurationSec();
            }

            // 카테고리
            List<String> tagNames = tagMap.getOrDefault(history.getContentId(), new ArrayList<>());
            String category = tagNames.isEmpty() ? null : String.join(", ", tagNames);

            // 시청 진행률(%)
            int lastPosition = history.getLastPositionSec() != null ? history.getLastPositionSec() : 0;
            int progressPercent = 0;
            if (duration > 0) {
                progressPercent = (int) (((double) lastPosition / duration) * 100);
                if (progressPercent > 100) progressPercent = 100;
            }

            return WatchHistoryResponse.builder()
                    .historyId(history.getId())
                    .contentId(history.getContentId())
                    .episodeId(history.getVideo().getId())
                    .title(history.getVideo().getContent().getTitle())
                    .episodeTitle(history.getVideo().getTitle())
                    .episodeNumber(history.getVideo().getEpisodeNo())
                    .thumbnailUrl(history.getVideo().getContent().getThumbnailUrl())
                    .contentType(history.getVideo().getContent().getType().name())
                    .category(category)
                    .lastPosition(history.getStatus() == HistoryStatus.STARTED ? null : lastPosition)
                    .duration(duration)
                    .progressPercent(progressPercent)
                    .status(history.getStatus().name())
                    .watchedAt(history.getLastWatchedAt())
                    .deletedAt(history.getDeletedAt())
                    .build();
        }).collect(Collectors.toList());

        return WatchHistoryListResponse.builder()
                .watchHistory(dtoList)
                .hasNext(false) // home의 watching-list는 페이징 처리를 하지 않고 최대 5개만 가져오므로 false
                .nextCursor(null)
                .build();
    }

    public List<DefaultContentResponse> getDefaultContents(String uploaderType, String tag, ContentAccessLevel accessLevel, ContentType contentType, Pageable pageable) {

        if (uploaderType != null && uploaderType.equals("CREATOR")) uploaderType = "USER";
        // 1. DB 레벨 필터링: 'ACTIVE' 상태, 제공자, 태그 조건이 모두 적용된 데이터 조회 (N+1 방지)
        Slice<Content> contentSlice = contentRepository.findContentsWithFilters(
                ContentStatus.ACTIVE, // 🔥 상태 파라미터 명시적 전달
                uploaderType,
                tag,
                accessLevel,
                contentType,
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

        // 삭제된 콘텐츠의 경우 상세페이지 조회X
        if (content.getStatus() != ContentStatus.ACTIVE) {
            throw new ContentNotFoundException(
                    "콘텐츠를 찾을 수 없습니다. contentId=" + contentId
            );
        }

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

        // 삭제된 콘텐츠의 경우 연관된 에피소드 조회X
        if (content.getStatus() != ContentStatus.ACTIVE) {
            throw new ContentNotFoundException(
                    "콘텐츠를 찾을 수 없습니다. contentId=" + contentId
            );
        }


//        if (content.getType() != ContentType.SERIES) {
//            throw new IllegalArgumentException(
//                    "시리즈 콘텐츠만 에피소드 목록을 조회할 수 있습니다. contentId=" + contentId
//            );
//        }


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