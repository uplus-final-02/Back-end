package org.backend.userapi.search.service;

import java.util.List;
import java.util.Map;

import org.backend.userapi.search.document.ContentDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ContentIndexingService {
    void indexAllContents();
    void indexContent(Long contentId);
    void deleteContent(Long contentId);
    Page<ContentDocument> search(String keyword, String category, String genre, String tag, Long userId, Pageable pageable);
    Page<ContentDocument> getAlternativeContents(Pageable pageable);
    long countIndexedContents();
    List<String> getSuggestions(String keyword);
    Map<String, Object> getIndexingStatus();
}