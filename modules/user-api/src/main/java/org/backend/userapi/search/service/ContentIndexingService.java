package org.backend.userapi.search.service;

import org.backend.userapi.search.document.ContentDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ContentIndexingService {

    void indexAllContents();

    void indexContent(Long contentId);

    void deleteContent(Long contentId);

    Page<ContentDocument> search(String keyword, Pageable pageable);

    long countIndexedContents();
}
