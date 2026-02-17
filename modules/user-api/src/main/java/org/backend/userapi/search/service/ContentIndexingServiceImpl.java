package org.backend.userapi.search.service;

import content.entity.Content;
import content.repository.ContentRepository;
import lombok.RequiredArgsConstructor;
import org.backend.userapi.search.document.ContentDocument;
import org.backend.userapi.search.repository.ContentSearchRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ContentIndexingServiceImpl implements ContentIndexingService {

    private final ContentRepository contentRepository;
    private final ContentSearchRepository contentSearchRepository;

    @Override
    @Transactional(readOnly = true)
    public void indexAllContents() {
        List<Content> contents = contentRepository.findAll();
        List<ContentDocument> documents = contents.stream()
                .map(this::toDocument)
                .toList();
        contentSearchRepository.saveAll(documents);
    }

    @Override
    @Transactional(readOnly = true)
    public void indexContent(Long contentId) {
        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new IllegalArgumentException("콘텐츠를 찾을 수 없습니다. id=" + contentId));
        contentSearchRepository.save(toDocument(content));
    }

    @Override
    public void deleteContent(Long contentId) {
        contentSearchRepository.deleteById(contentId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ContentDocument> search(String keyword, Pageable pageable) {
        if (keyword == null || keyword.isBlank()) {
            return contentSearchRepository.findAll(pageable);
        }
        return contentSearchRepository.searchByKeyword(keyword, pageable);
    }

    @Override
    public long countIndexedContents() {
        return contentSearchRepository.count();
    }

    private ContentDocument toDocument(Content content) {
        return ContentDocument.builder()
                .contentId(content.getId())
                .title(content.getTitle())
                .description(content.getDescription())
                .type(content.getType().name())
                .status(content.getStatus().name())
                .accessLevel(content.getAccessLevel().name())
                .thumbnailUrl(content.getThumbnailUrl())
                .totalViewCount(content.getTotalViewCount())
                .bookmarkCount(content.getBookmarkCount())
                .createdAt(content.getCreatedAt())
                .updatedAt(content.getUpdatedAt())
                .build();
    }
}
