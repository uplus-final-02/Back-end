package org.backend.userapi.content.service;

import content.entity.Content;
import content.entity.WatchHistory;
import content.repository.ContentRepository;
import content.repository.WatchHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.backend.userapi.common.exception.ContentNotFoundException;
import org.backend.userapi.content.dto.ContentDetailResponse;
import org.backend.userapi.content.dto.WatchingContentResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContentService {

    private final WatchHistoryRepository watchHistoryRepository;
    private final ContentRepository contentRepository;

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
}
