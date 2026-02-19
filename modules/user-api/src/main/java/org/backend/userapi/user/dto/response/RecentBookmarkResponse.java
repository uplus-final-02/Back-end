package org.backend.userapi.user.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecentBookmarkResponse {
    private Long bookmarkId;
    private Long contentId;
    private String title;       // 콘텐츠 제목
    private String thumbnailUrl;   // 콘텐츠 썸네일/포스터
    private LocalDateTime bookmarkedAt; // 찜한 날짜
}