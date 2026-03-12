package org.backend.userapi.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "찜 목록 무한 스크롤 응답 데이터")
public record BookmarkListResponse(
    @Schema(description = "찜한 콘텐츠 목록")
    List<BookmarkItemResponse> bookmarks,
    
    @Schema(description = "다음 페이지 조회를 위한 커서 (마지막 항목의 찜 ID), 더 이상 없으면 null", example = "45", nullable = true)
    String nextCursor,
    
    @Schema(description = "다음 페이지 존재 여부", example = "true")
    boolean hasNext,
    
    @Schema(description = "총 찜한 콘텐츠 개수", example = "120")
    long totalCount
) {
    @Schema(description = "찜 항목 상세 정보")
    public record BookmarkItemResponse(
        @Schema(description = "찜 기록의 고유 ID (커서로 사용됨)", example = "45")
        Long bookmarkId,
        
        @Schema(description = "콘텐츠 고유 ID", example = "18")
        Long contentId,
        
        @Schema(description = "콘텐츠 제목", example = "무빙")
        String title,
        
        @Schema(description = "콘텐츠 썸네일 URL", example = "https://example.com/moving.jpg")
        String thumbnailUrl,
        
        @Schema(description = "콘텐츠 타입", example = "SERIES")
        String contentType,
        
        @Schema(description = "카테고리 분류", example = "전체")
        String category,
        
        @Schema(description = "찜한 시간", example = "2024-03-11T14:20:00")
        String bookmarkedAt,
        
        @Schema(description = "원본 콘텐츠 삭제 여부 (true면 재생 불가 처리 필요)", example = "false")
        boolean isDeleted 
    ) {}
}