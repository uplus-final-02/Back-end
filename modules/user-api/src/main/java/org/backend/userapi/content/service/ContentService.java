package org.backend.userapi.content.service;

import content.entity.Content;
import content.entity.WatchHistory;
import content.repository.ContentRepository;
import content.repository.ContentTagRepository;
import content.repository.WatchHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.backend.userapi.content.dto.DefaultContentResponse;
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
    public List<DefaultContentResponse> getContents(String category) {
        List<Content> contents;
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");

        if ("ADMIN".equalsIgnoreCase(category)) {
            // 관리자 업로드: uploaderId가 NULL인 것 조회
            contents = contentRepository.findByUploaderIdIsNull(sort);
        } else if ("USER".equalsIgnoreCase(category)) {
            // 일반 유저 업로드: uploaderId가 NULL이 아닌 것 조회
            contents = contentRepository.findByUploaderIdIsNotNull(sort);
        } else {
            // 전체 조회
            contents = contentRepository.findAll(sort);
        }

        if (contents.isEmpty()) {
            return Collections.emptyList();
        }

        // 1. uploaderId 목록 수집 (중복 제거 및 null 제외)
        Set<Long> uploaderIds = contents.stream()
                .map(Content::getUploaderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // 2. User 일괄 조회 (findAllById)
        Map<Long, String> uploaderNicknameMap = new HashMap<>();
        if (!uploaderIds.isEmpty()) {
            List<User> uploaders = userRepository.findAllById(new ArrayList<>(uploaderIds));
            for (User u : uploaders) {
                uploaderNicknameMap.put(u.getId(), u.getNickname());
            }
        }

        // 3. DTO 변환 (Map에서 닉네임 조회)
        return contents.stream()
                .map(content -> {
                    // uploaderId가 null이면 "관리자", 아니면 닉네임 조회
                    String uploaderName = "관리자";
                    if (content.getUploaderId() != null) {
                        uploaderName = uploaderNicknameMap.getOrDefault(content.getUploaderId(), "알 수 없음");
                    }
                    return DefaultContentResponse.from(content, uploaderName);
                })
                .collect(Collectors.toList());
    }
}
