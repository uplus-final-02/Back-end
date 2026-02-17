package org.backend.userapi.interaction.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.backend.userapi.interaction.dto.response.BookmarkListResponse;
import org.backend.userapi.interaction.dto.response.BookmarkListResponse.BookmarkItemResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import common.enums.ContentStatus;
import content.entity.Content;
import content.repository.ContentRepository;
import interaction.entity.Bookmark;
import interaction.repository.BookmarkRepository;
import lombok.RequiredArgsConstructor;
import user.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class BookmarkService {

    private final BookmarkRepository bookmarkRepository;
    private final ContentRepository contentRepository;
    private final UserRepository userRepository;

    /**
     * AE2-44: 찜하기 등록
     */
    @Transactional
    public void addBookmark(Long userId, Long contentId) {
        // 1. 중복 체크
        if (bookmarkRepository.existsByUserIdAndContentId(userId, contentId)) {
            return;
        }

        // 2. 존재 여부 검증 (객체 대신 존재 확인만 수행)
        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException("사용자를 찾을 수 없습니다.");
        }
        
        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new IllegalArgumentException("콘텐츠를 찾을 수 없습니다."));

        // 3. 찜 저장 (수정된 Entity 구조: ID 직접 저장)
        bookmarkRepository.save(Bookmark.builder()
                .userId(userId)    // 객체(user) 대신 ID(userId) 전달
                .contentId(contentId) // 객체(content) 대신 ID(contentId) 전달
                .build());

        // 4. 콘텐츠 테이블의 북마크 총 수 증가
        content.updateBookmarkCount(content.getBookmarkCount() + 1);
    }

    /**
     * AE2-43: 찜 목록 조회 (Cursor 기반 페이징)
     */
    @Transactional(readOnly = true)
    public BookmarkListResponse getMyBookmarks(Long userId, Long cursorId, int size) {
        // 1. 북마크 목록 먼저 조회
        List<Bookmark> bookmarks = bookmarkRepository.findByUserIdWithCursor(
                userId, cursorId, PageRequest.of(0, size + 1));

        boolean hasNext = bookmarks.size() > size;
        if (hasNext) {
            bookmarks = bookmarks.subList(0, size);
        }

        // 2. 조회된 북마크들에서 contentId 목록 추출
        List<Long> contentIds = bookmarks.stream()
                .map(Bookmark::getContentId)
                .toList();

        // 3. 연관된 콘텐츠 정보 한꺼번에 조회 (IN 쿼리 사용)
        Map<Long, Content> contentMap = contentRepository.findAllById(contentIds).stream()
                .collect(Collectors.toMap(Content::getId, c -> c));

        // 4. DTO 변환 (contentMap에서 정보 매핑)
        List<BookmarkItemResponse> items = bookmarks.stream().map(b -> {
            Content content = contentMap.get(b.getContentId());
            return new BookmarkItemResponse(
                b.getId(),
                b.getContentId(),
                content != null ? content.getTitle() : "알 수 없는 콘텐츠",
                content != null ? content.getThumbnailUrl() : null,
                content != null ? content.getType().name() : "UNKNOWN",
                "전체",
                b.getCreatedAt().toString(),
                content == null || content.getStatus() == ContentStatus.DELETED
            );
        }).toList();

        String nextCursor = hasNext ? String.valueOf(bookmarks.get(bookmarks.size() - 1).getId()) : null;

        return new BookmarkListResponse(items, nextCursor, hasNext, bookmarkRepository.countByUserId(userId));
    }
}