package org.backend.userapi.user.service;

import common.enums.HistoryStatus;
import common.repository.TagRepository;
import content.repository.ContentTagRepository;
import content.repository.WatchHistoryRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.backend.userapi.user.dto.response.GenreStatisticsResponse;
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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WatchHistoryService {

  private final WatchHistoryRepository watchHistoryRepository;
  private final ContentTagRepository contentTagRepository;
  private final TagRepository tagRepository;

  public WatchHistoryListResponse getWatchHistories(Long userId, Long cursor, Pageable pageable) {

    Slice<WatchHistory> slice = watchHistoryRepository.findHistoriesByCursor(userId, cursor, pageable);

    List<Long> contentIds = slice.getContent().stream()
        .map(WatchHistory::getContentId)
        .distinct()
        .collect(Collectors.toList());

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

  @Transactional
  public void deleteWatchHistory(Long userId, Long historyId) {
    WatchHistory history = watchHistoryRepository.findByIdAndUserId(historyId, userId)
        .orElseThrow(() -> new IllegalArgumentException("시청 이력을 찾을 수 없습니다."));

    watchHistoryRepository.delete(history);
  }

  public WatchStatisticsResponse getWatchStatistics(Long userId) {

    // 0회/0시간으로 기본 맵(Map) 세팅
    List<Object[]> priorityTags = tagRepository.findPriorityTags();
    Map<Long, GenreStatTracker> statMap = new HashMap<>();
    for (Object[] pt : priorityTags) {
      Long tId = (Long) pt[0];
      String tName = (String) pt[1];
      statMap.put(tId, new GenreStatTracker(tId, tName));
    }

    List<WatchHistory> histories = watchHistoryRepository.findAllByUserIdForStatistics(userId);

    int totalWatchedCount = 0;
    int totalWatchTime = 0;

    List<Long> contentIds = histories.stream()
        .map(WatchHistory::getContentId)
        .distinct()
        .collect(Collectors.toList());

    Map<Long, TagInfo> primaryTagMap = new HashMap<>();
    if (!contentIds.isEmpty()) {
      List<Object[]> tagResults = contentTagRepository.findPriorityTagDetailsByContentIds(contentIds);
      for (Object[] result : tagResults) {
        Long cId = (Long) result[0];
        if (!primaryTagMap.containsKey(cId)) {
          primaryTagMap.put(cId, new TagInfo((Long) result[1], (String) result[2]));
        }
      }
    }

    // 데이터 누적
    for (WatchHistory history : histories) {
      boolean isCompleted = history.getStatus() == HistoryStatus.COMPLETED;
      int watchTime = history.getLastPositionSec() != null ? history.getLastPositionSec() : 0;

      // 전체 누적
      if (isCompleted) totalWatchedCount++;
      totalWatchTime += watchTime;

      TagInfo tag = primaryTagMap.get(history.getContentId());
      if (tag != null && statMap.containsKey(tag.getTagId())) {
        GenreStatTracker tracker = statMap.get(tag.getTagId());
        if (isCompleted) tracker.watchedCount++;
        tracker.watchTime += watchTime;
      }
    }

    final int finalTotalWatchedCount = totalWatchedCount;

    // 많이 본 순서로 정렬
    List<GenreStatisticsResponse> statisticsByGenre = statMap.values().stream()
        .map(tracker -> {
          double percentage = finalTotalWatchedCount > 0 ?
              Math.round(((double) tracker.watchedCount / finalTotalWatchedCount) * 1000) / 10.0 : 0.0;

          return GenreStatisticsResponse.builder()
              .tagId(tracker.tagId)
              .tagName(tracker.tagName)
              .watchedCount(tracker.watchedCount)
              .watchTime(tracker.watchTime)
              .percentage(percentage)
              .build();
        })
        .sorted((a, b) -> b.getWatchedCount().compareTo(a.getWatchedCount()))
        .collect(Collectors.toList());

    String updatedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();

    return WatchStatisticsResponse.builder()
        .totalWatchedCount(totalWatchedCount)
        .totalWatchTime(totalWatchTime)
        .statisticsByGenre(statisticsByGenre)
        .updatedAt(updatedAt)
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