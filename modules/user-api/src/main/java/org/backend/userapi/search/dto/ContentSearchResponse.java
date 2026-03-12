package org.backend.userapi.search.dto;

import java.util.List;

import org.backend.userapi.search.document.ContentDocument;
import org.springframework.data.domain.Page;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "검색 결과 전체 응답 객체")
public record ContentSearchResponse(
		
		@Schema(description = "검색된 개별 콘텐츠 목록")
		List<ContentSearchItem> contents,
		
		@Schema(description = "다음 페이지 존재 여부 (무한 스크롤용)", example = "true")
	    boolean hasNext
) {
	public static ContentSearchResponse from(Page<ContentDocument> searchPage, String keyword) {
        return new ContentSearchResponse(
            searchPage.getContent().stream()
                .map(doc -> ContentSearchItem.from(doc, keyword))
                .toList(),
            searchPage.hasNext() 
        );
    }
}