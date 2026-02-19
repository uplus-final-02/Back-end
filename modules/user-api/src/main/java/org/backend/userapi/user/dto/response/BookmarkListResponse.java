package org.backend.userapi.user.dto.response;

import java.util.List;

public record BookmarkListResponse(
    List<BookmarkItemResponse> bookmarks,
    String nextCursor,
    boolean hasNext,
    long totalCount
) {
    public record BookmarkItemResponse(
        Long bookmarkId,
        Long contentId,
        String title,
        String thumbnailUrl,
        String contentType,
        String category,
        String bookmarkedAt,
        boolean isDeleted // 삭제 여부 반영
    ) {}
}