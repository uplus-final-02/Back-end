package org.backend.userapi.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Schema(description = "찜 목록 연속 재생 플레이리스트 응답 데이터")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class BookmarkPlaylistResponse {

    @Schema(description = "연속 재생 큐(Queue)에 들어갈 에피소드 목록")
    private List<PlaylistItem> playlist;
    
    @Schema(description = "플레이리스트 전체 개수", example = "42")
    private int totalCount;

    @Schema(description = "플레이리스트 단일 항목 정보")
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlaylistItem {
        @Schema(description = "재생 순서", example = "1")
        private int order;             
        
        @Schema(description = "콘텐츠 고유 ID", example = "18")
        private Long contentId;        
        
        @Schema(description = "에피소드 고유 ID (단건 영화인 경우 null)", example = "105", nullable = true)
        private Long episodeId;        
        
        @Schema(description = "콘텐츠 제목", example = "무빙")
        private String title;          
        
        @Schema(description = "에피소드 제목 (예: '1화 - 부제')", example = "1화 - 초능력", nullable = true)
        private String episodeTitle;   
        
        @Schema(description = "썸네일 URL (에피소드 썸네일 우선, 없으면 콘텐츠 썸네일)", example = "https://example.com/thumb.jpg")
        private String thumbnailUrl;   
        
        @Schema(description = "실제 재생할 영상 주소 (HLS URL 우선)", example = "https://example.com/video.m3u8")
        private String videoUrl;       
        
        @Schema(description = "영상 총 길이 (초)", example = "3600")
        private int duration;          
        
        @Schema(description = "마지막으로 보던 위치 (초)", example = "1200")
        private int lastPosition;      
        
        @Schema(description = "진행률 (%)", example = "33")
        private int progressPercent;   
    }
}