package org.backend.userapi.search.service;

import java.util.List; // 🚨 List 임포트 필수
import java.util.Map;

import org.backend.userapi.search.document.ContentDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ContentIndexingService {
	void indexAllContents();
    void indexContent(Long contentId);
    void deleteContent(Long contentId);
    Page<ContentDocument> search(String keyword, Pageable pageable);
    long countIndexedContents();
    List<String> getSuggestions(String keyword);
    
    // 🚨 [핵심] 이 줄이 없어서 에러가 난 것입니다. 추가해주세요!
    Map<String, Object> getIndexingStatus();
}