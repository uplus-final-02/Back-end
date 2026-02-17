package org.backend.userapi.interaction.service;

import java.util.List;

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
import user.entity.User;
import user.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class BookmarkService {

    private final BookmarkRepository bookmarkRepository;
    private final ContentRepository contentRepository; // 등록 기능을 위해 추가
    private final UserRepository userRepository;       // 등록 기능을 위해 추가

    /**
     * AE2-44: 찜하기 등록
     */
    @Transactional
    public void addBookmark(Long userId, Long contentId) {
        // 1. 중복 체크 (Repository의 existsByUserIdAndContentId 사용)
        if (bookmarkRepository.existsByUserIdAndContentId(userId, contentId)) {
            return;
        }

        // 2. 엔티티 조회 및 검증
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new IllegalArgumentException("콘텐츠를 찾을 수 없습니다."));

        // 3. 찜 저장
        bookmarkRepository.save(Bookmark.builder()
                .user(user)
                .content(content)
                .build());
        
        // 4. 콘텐츠 테이블의 북마크 총 수 증가
        content.updateBookmarkCount(content.getBookmarkCount() + 1);
    }

    /**
     * AE2-43: 찜 목록 조회 (Cursor 기반 페이징)
     */
    @Transactional(readOnly = true)
    public BookmarkListResponse getMyBookmarks(Long userId, Long cursorId, int size) {
        // 다음 페이지 존재 확인을 위해 size + 1개 조회
        List<Bookmark> bookmarks = bookmarkRepository.findByUserIdWithCursor(
                userId, cursorId, PageRequest.of(0, size + 1));

        boolean hasNext = bookmarks.size() > size;
        if (hasNext) {
            bookmarks = bookmarks.subList(0, size);
        }

        List<BookmarkItemResponse> items = bookmarks.stream().map(b -> new BookmarkItemResponse(
            b.getId(),
            b.getContent().getId(),
            b.getContent().getTitle(),
            b.getContent().getThumbnailUrl(),
            b.getContent().getType().name(),
            "전체", // 카테고리 로직 (추후 확장)
            b.getCreatedAt().toString(),
            b.getContent().getStatus() == ContentStatus.DELETED // 삭제 여부 판별
        )).toList();

        // 마지막 데이터 ID를 다음 커서로 사용
        String nextCursor = hasNext ? String.valueOf(bookmarks.get(bookmarks.size() - 1).getId()) : null;

        return new BookmarkListResponse(items, nextCursor, hasNext, bookmarkRepository.countByUserId(userId));
    }
}