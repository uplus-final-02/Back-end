package org.backend.userapi.user.service;

import common.enums.HistoryStatus;
import lombok.RequiredArgsConstructor;
import org.backend.userapi.user.dto.response.WatchHistoryDto;
import org.backend.userapi.user.dto.response.WatchHistoryListResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import user.entity.History;
import user.repository.HistoryRepository;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WatchHistoryService {

  private final HistoryRepository historyRepository;

  public WatchHistoryListResponse getWatchHistories(Long userId, Pageable pageable) {

    // Repository에서 Slice 타입으로 데이터 조회
    Slice<History> slice = historyRepository.findSliceByUserIdOrderByUpdatedAtDesc(userId, pageable);

    // Entity 리스트를 DTO 리스트로 변환
    List<WatchHistoryDto> dtoList = slice.getContent().stream().map(history -> {

      // 영상 길이
      int duration = 0;
      if (history.getVideo().getVideoFile() != null) {
        duration = history.getVideo().getVideoFile().getDurationSec();
      }

      // 카테고리 가져오기
      List<String> category = new java.util.ArrayList<>();

      if (history.getContent().getContentTags() != null && !history.getContent().getContentTags().isEmpty()) {
        // 모든 태그 이름을 꺼내서 List로 만듭니다.
        category = history.getContent().getContentTags().stream()
            .map(contentTag -> contentTag.getTag().getName())
            .collect(Collectors.toList());
      } else {
        // 태그가 하나도 없으면 기본값 세팅
        category.add("기타");
      }

      // 시청 진행률(%)
      int lastPosition = history.getLastPositionSec() != null ? history.getLastPositionSec() : 0;
      int progressPercent = 0;

      if (duration > 0) {
        progressPercent = (int) (((double) lastPosition / duration) * 100);
        if (progressPercent > 100) {
          progressPercent = 100;
        }
      }

      return WatchHistoryDto.builder()
          .historyId(history.getId())
          .contentId(history.getContent().getId())
          .episodeId(history.getVideo().getId())
          .title(history.getContent().getTitle())
          .episodeTitle(history.getVideo().getTitle())
          .episodeNumber(history.getVideo().getEpisodeNo())
          .thumbnailUrl(history.getContent().getThumbnailUrl())
          .contentType(history.getContent().getType().name())
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
    String nextCursor = slice.hasNext() ? String.valueOf(pageable.getPageNumber() + 1) : null;

    return WatchHistoryListResponse.builder()
        .watchHistory(dtoList)
        .nextCursor(nextCursor)
        .hasNext(slice.hasNext())
        .build();
  }

  @Transactional
  public void deleteWatchHistory(Long userId, Long historyId) {
    History history = historyRepository.findByIdAndUserId(historyId, userId)
        .orElseThrow(() -> new IllegalArgumentException("시청 이력을 찾을 수 없습니다."));

    historyRepository.delete(history);
  }

}