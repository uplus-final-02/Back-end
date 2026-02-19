package org.backend.userapi.search.dto;

import org.backend.userapi.search.document.ContentDocument;
import org.springframework.data.domain.Page;
import java.util.List;

public record ContentSearchResponse(
		List<ContentSearchItem> contents,
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