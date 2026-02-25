package org.backend.userapi.user.service;

import common.enums.HistoryStatus;
import content.repository.ContentTagRepository;
import content.repository.WatchHistoryRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.backend.userapi.user.dto.response.WatchHistoryDto;
import org.backend.userapi.user.dto.response.WatchHistoryListResponse;
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

    List<WatchHistoryDto> dtoList = slice.getContent().stream().map(history -> {

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

      return WatchHistoryDto.builder()
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

}