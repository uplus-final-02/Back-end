package org.backend.userapi.user.service;

import common.enums.HistoryStatus;
import common.repository.TagRepository;
import content.entity.UserWatchHistory;
import content.repository.ContentTagRepository;
import content.repository.UserWatchHistoryRepository;
import content.repository.WatchHistoryRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.backend.userapi.user.dto.response.UserWatchHistoryGroupResponse;
import org.backend.userapi.user.dto.response.GenreStatisticsResponse;
import org.backend.userapi.user.dto.response.UserWatchHistoryListResponse;
import org.backend.userapi.user.dto.response.UserWatchHistoryResponse;
import org.backend.userapi.user.dto.response.WatchHistoryResponse;
import org.backend.userapi.user.dto.response.WatchHistoryListResponse;
import org.backend.userapi.user.dto.response.WatchStatisticsResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import content.entity.WatchHistory;

import java.util.List;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WatchHistoryService {

    private final WatchHistoryRepository watchHistoryRepository;
    private final UserWatchHistoryRepository userWatchHistoryRepository;
    private final ContentTagRepository contentTagRepository;
    private final TagRepository tagRepository;

    public WatchHistoryListResponse getWatchHistories(Long userId, Long cursor, Pageable pageable) {

        // 데이터 조회 & 콘텐츠 ID 추출
        Slice<WatchHistory> slice = watchHistoryRepository.findHistoriesByCursor(userId, cursor, pageable);

        List<Long> contentIds = slice.getContent().stream()
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

        List<WatchHistoryResponse> dtoList = slice.getContent().stream().map(history -> {

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

        // 커서 생성
        String nextCursor = null;
        if (slice.hasNext() && !dtoList.isEmpty()) {
            Long lastHistoryId = dtoList.get(dtoList.size() - 1).getHistoryId();
            nextCursor = String.valueOf(lastHistoryId);
        }

        return WatchHistoryListResponse.builder()
                .watchHistory(dtoList)
                .nextCursor(nextCursor)
                .hasNext(slice.hasNext())
                .build();
    }

    public UserWatchHistoryListResponse getUserWatchHistories(Long userId) {
        LocalDateTime threeMonthsAgo = LocalDateTime.now().minusMonths(3);

        List<UserWatchHistory> histories = userWatchHistoryRepository.findUserWatchHistoriesByUserIdAndSince(userId, threeMonthsAgo);

        // 1. 전체 시청 기록을 최신순 정렬 후 parent_content_id를 기준으로 그룹화 (LinkedHashMap을 통해 그룹 자체의 최신순 순서도 보장)
        Map<Long, List<UserWatchHistory>> groupedMap = histories.stream()
                .sorted((a, b) -> b.getLastWatchedAt().compareTo(a.getLastWatchedAt()))
                .collect(groupingBy(
                        history -> history.getUserContent().getParentContent().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        // 2. 그룹별 데이터를 프론트엔드 맞춤형 DTO(플레이리스트 형태)로 매핑
        List<UserWatchHistoryGroupResponse> groupedHistories = groupedMap.entrySet().stream()
                .map(entry -> {
                    Long parentId = entry.getKey();
                    List<UserWatchHistory> historyList = entry.getValue();

                    // 그룹 내 첫 번째 요소에서 부모 콘텐츠 메타데이터 추출
                    var parentContent = historyList.get(0).getUserContent().getParentContent();

                    List<UserWatchHistoryResponse> historyResponses = historyList.stream()
                            .map(history -> UserWatchHistoryResponse.builder()
                                    .historyId(history.getId())
                                    .userContentId(history.getUserContent().getId())
                                    .parentContentId(parentId)
                                    .title(history.getUserContent().getTitle())
                                    .description(history.getUserContent().getDescription())
                                    .thumbnailUrl(history.getUserContent().getThumbnailUrl())
                                    .contentStatus(history.getUserContent().getContentStatus().name())
                                    .lastWatchedAt(history.getLastWatchedAt())
                                    .deletedAt(history.getDeletedAt())
                                    .build())
                            .collect(Collectors.toList());

                    return UserWatchHistoryGroupResponse.builder()
                            .parentContentId(parentId)
                            .parentTitle(parentContent.getTitle())
                            .parentThumbnailUrl(parentContent.getThumbnailUrl())
                            .histories(historyResponses)
                            .build();
                })
                .collect(Collectors.toList());

        return UserWatchHistoryListResponse.builder()
                .watchHistories(groupedHistories)
                .build();
    }

    @Transactional
    public void deleteWatchHistory(Long userId, Long historyId) {
        WatchHistory history = watchHistoryRepository.findByIdAndUserId(historyId, userId)
                .orElseThrow(() -> new IllegalArgumentException("시청 이력을 찾을 수 없습니다."));

        watchHistoryRepository.delete(history);
    }

    public WatchStatisticsResponse getWatchStatistics(Long userId) {

        List<Object[]> priorityTags = tagRepository.findPriorityTags();
        Map<Long, GenreStatTracker> statMap = new HashMap<>();
        for (Object[] pt : priorityTags) {
            statMap.put((Long) pt[0], new GenreStatTracker((Long) pt[0], (String) pt[1]));
        }

        List<WatchHistory> histories = watchHistoryRepository.findAllByUserIdForStatistics(userId);

        Set<Long> completedContentIds = histories.stream()
                .filter(h -> h.getStatus() == HistoryStatus.COMPLETED)
                .map(WatchHistory::getContentId)
                .collect(Collectors.toSet());

        int totalWatchedCount = completedContentIds.size();
        int totalWatchTime = 0;

        List<Long> contentIds = histories.stream()
                .map(WatchHistory::getContentId)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, List<TagInfo>> multiTagMap = new HashMap<>();
        if (!contentIds.isEmpty()) {
            List<Object[]> tagResults = contentTagRepository.findPriorityTagDetailsByContentIds(contentIds);
            for (Object[] result : tagResults) {
                Long cId = (Long) result[0];
                TagInfo tagInfo = new TagInfo((Long) result[1], (String) result[2]);
                multiTagMap.computeIfAbsent(cId, k -> new ArrayList<>()).add(tagInfo);
            }
        }

        for (WatchHistory history : histories) {
            int watchTime = history.getLastPositionSec() != null ? history.getLastPositionSec() : 0;
            totalWatchTime += watchTime; // 모든 에피소드의 시청 시간을 합산

            List<TagInfo> tags = multiTagMap.getOrDefault(history.getContentId(), new ArrayList<>());
            for (TagInfo tag : tags) {
                GenreStatTracker tracker = statMap.get(tag.getTagId());
                if (tracker != null) {
                    tracker.watchTime += watchTime; // 장르별 시청 시간 업데이트
                }
            }
        }

        for (Long cId : completedContentIds) {
            List<TagInfo> tags = multiTagMap.getOrDefault(cId, new ArrayList<>());
            for (TagInfo tag : tags) {
                GenreStatTracker tracker = statMap.get(tag.getTagId());
                if (tracker != null) {
                    tracker.watchedCount++; // 작품당 한번 카운트
                }
            }
        }

        int sumOfAllGenreCounts = statMap.values().stream()
                .mapToInt(tracker -> tracker.watchedCount)
                .sum();

        // 장르별 비율(%) 계산 + 비시청 장르(통계 X) 필터링 + 정렬
        List<GenreStatisticsResponse> statisticsByGenre = statMap.values().stream()
                .filter(tracker -> tracker.watchedCount > 0)
                .map(tracker -> {
                    double percentage = sumOfAllGenreCounts > 0 ?
                            Math.round(((double) tracker.watchedCount / sumOfAllGenreCounts) * 1000) / 10.0 : 0.0;

                    return GenreStatisticsResponse.builder()
                            .tagId(tracker.tagId).tagName(tracker.tagName)
                            .watchedCount(tracker.watchedCount).watchTime(tracker.watchTime)
                            .percentage(percentage).build();
                })
                .sorted((a, b) -> b.getWatchedCount().compareTo(a.getWatchedCount()))
                .collect(Collectors.toList());

        return WatchStatisticsResponse.builder()
                .totalWatchedCount(totalWatchedCount)
                .totalWatchTime(totalWatchTime)
                .statisticsByGenre(statisticsByGenre)
                .updatedAt(Instant.now().truncatedTo(ChronoUnit.SECONDS).toString())
                .build();
    }

    @Getter
    @AllArgsConstructor
    private static class TagInfo {
        private Long tagId;
        private String tagName;
    }

    private static class GenreStatTracker {
        Long tagId;
        String tagName;
        int watchedCount = 0;
        int watchTime = 0;

        public GenreStatTracker(Long tagId, String tagName) {
            this.tagId = tagId;
            this.tagName = tagName;
        }
    }

}