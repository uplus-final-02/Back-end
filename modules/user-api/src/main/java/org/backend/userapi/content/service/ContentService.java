package org.backend.userapi.content.service;

import common.entity.Tag;
import common.enums.ContentStatus;
import common.enums.ContentType;
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
import org.backend.userapi.content.dto.WatchingContentResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import user.entity.User;
import user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContentService {

    private final ContentRepository contentRepository;
    private final ContentTagRepository contentTagRepository;
    private final WatchHistoryRepository watchHistoryRepository;
    private final UserRepository userRepository;
    private final VideoRepository videoRepository;

    public List<WatchingContentResponse> getWatchingContents(Long userId) {
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

        // 3. DTO 변환 (별도의 조회 쿼리 없이 바로 변환 가능)
        return distinctHistoryMap.values().stream()
                .map(wh -> WatchingContentResponse.builder()
                        .contentId(wh.getVideo().getContent().getId())
                        .title(wh.getVideo().getContent().getTitle())
                        .thumbnailUrl(wh.getVideo().getContent().getThumbnailUrl())
                        .lastVideoId(wh.getVideo().getId())
                        .lastVideoTitle(wh.getVideo().getTitle())
                        .currentPositionSec(wh.getLastPositionSec())
                        .lastWatchedAt(wh.getLastWatchedAt())
                        .build())
                .collect(Collectors.toList());
    }

    // 기본 콘텐츠 리스트 조회
//    public List<DefaultContentResponse> getDefaultContents(String uploaderType, String tag) {
//        List<Content> contents;
//        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
//
//        // 제공자별 필터링
//        if ("ADMIN".equalsIgnoreCase(uploaderType)) {
//            // 관리자 업로드: uploaderId가 NULL인 것 조회
//            contents = contentRepository.findByUploaderIdIsNull(sort);
//        } else if ("USER".equalsIgnoreCase(uploaderType)) {
//            // 일반 유저 업로드: uploaderId가 NULL이 아닌 것 조회
//            contents = contentRepository.findByUploaderIdIsNotNull(sort);
//        } else {
//            // 전체 조회
//            contents = contentRepository.findAll(sort);
//        }
//
//        // 상태 필터링 (ACTIVE 상태만) 및 태그 필터링 (태그도 ACTIVE 상태만)
//        contents = contents.stream()
//                .filter(content -> content.getStatus() == ContentStatus.ACTIVE)
//                .filter(content -> {
//                    if (tag != null && !tag.isEmpty()) {
//                        return content.getTags().stream()
//                                .filter(Tag::getIsActive)
//                                .anyMatch(t -> t.getName().equalsIgnoreCase(tag));
//                    }
//                    return true;
//                })
//                .collect(Collectors.toList());
//
//        if (contents.isEmpty()) {
//            return Collections.emptyList();
//        }
//
//        // 1. uploaderId 목록 수집 (중복 제거 및 null 제외)
//        Set<Long> uploaderIds = contents.stream()
//                .map(Content::getUploaderId)
//                .filter(Objects::nonNull)
//                .collect(Collectors.toSet());
//
//        // 2. User 일괄 조회 (findAllById)
//        Map<Long, String> uploaderNicknameMap = new HashMap<>();
//        if (!uploaderIds.isEmpty()) {
//            List<User> uploaders = userRepository.findAllById(new ArrayList<>(uploaderIds));
//            for (User u : uploaders) {
//                uploaderNicknameMap.put(u.getId(), u.getNickname());
//            }
//        }
//
//        // 3. DTO 변환 (Map에서 닉네임 조회)
//        return contents.stream()
//                .map(content -> {
//                    // uploaderId가 null이면 "관리자", 아니면 닉네임 조회
//                    String uploaderName = "관리자";
//                    if (content.getUploaderId() != null) {
//                        uploaderName = uploaderNicknameMap.getOrDefault(content.getUploaderId(), "알 수 없음");
//                    }
//
//                    // 태그 필터링 (ACTIVE 상태만)
//                    List<Tag> activeTags = content.getTags().stream()
//                            .filter(Tag::getIsActive)
//                            .collect(Collectors.toList());
//
//                    // 오버로딩된 from 메소드 사용 (필터링된 태그 전달)
//                    return DefaultContentResponse.from(content, uploaderName, activeTags);
//                })
//                .collect(Collectors.toList());
//    }
    public List<DefaultContentResponse> getDefaultContents(String uploaderType, String tag) {

        // 1. DB 레벨 필터링: 'ACTIVE' 상태, 제공자, 태그 조건이 모두 적용된 데이터 조회 (N+1 방지)
        List<Content> contents = contentRepository.findContentsWithFilters(
                ContentStatus.ACTIVE, // 🔥 상태 파라미터 명시적 전달
                uploaderType,
                tag
        );

        if (contents.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. User 닉네임 일괄 조회 (in 쿼리 최적화)
        Map<Long, String> uploaderNicknameMap = getUploaderNicknameMap(contents);

        // 3. DTO 변환 및 반환
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
        return userRepository.findAllById(uploaderIds).stream()
                .collect(Collectors.toMap(User::getId, User::getNickname));
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
