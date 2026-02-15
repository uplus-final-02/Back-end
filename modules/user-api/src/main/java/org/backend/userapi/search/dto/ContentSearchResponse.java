package org.backend.userapi.search.dto;

import org.backend.userapi.search.document.ContentDocument;
import org.springframework.data.domain.Page;

import java.util.List;

public record ContentSearchResponse(
        List<ContentSearchItem> contents,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static ContentSearchResponse from(Page<ContentDocument> searchPage) {
        return new ContentSearchResponse(
                searchPage.getContent().stream().map(ContentSearchItem::from).toList(),
                searchPage.getNumber(),
                searchPage.getSize(),
                searchPage.getTotalElements(),
                searchPage.getTotalPages()
        );
    }
}
