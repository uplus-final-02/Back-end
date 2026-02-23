package org.backend.userapi.user.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class BookmarkPlaylistResponse {

    private List<PlaylistItem> playlist;
    private int totalCount;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlaylistItem {
        private int order;             // 1. 재생 순서
        private Long contentId;        // 2. 콘텐츠 ID
        private Long episodeId;        // 3. 에피소드 ID (null 가능)
        private String title;          // 4. 콘텐츠 제목
        private String episodeTitle;   // 5. 에피소드 제목 (null 가능)
        private String thumbnailUrl;   // 6. 썸네일
        private String videoUrl;       // 7. 영상 주소
        private int duration;          // 8. 영상 길이 (초)
        private int lastPosition;      // 9. 보던 위치 (초)
        private int progressPercent;   // 10. 진행률 (%)
    }
}