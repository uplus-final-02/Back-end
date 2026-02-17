package org.backend.userapi.search.service;

import java.util.List;

import org.backend.userapi.search.document.ContentDocument;
import org.backend.userapi.search.repository.ContentSearchRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import common.entity.Tag;
import content.entity.Content;
import content.repository.ContentRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ContentIndexingServiceImpl implements ContentIndexingService {

    private final ContentRepository contentRepository;
    private final ContentSearchRepository contentSearchRepository;
    private final ElasticsearchOperations elasticsearchOperations;

    @Override
    @Transactional(readOnly = true)
    public void indexAllContents() {
        List<Content> contents = contentRepository.findAll();
        List<ContentDocument> documents = contents.stream().map(this::toDocument).toList();
        contentSearchRepository.saveAll(documents);
    }

    @Override
    @Transactional(readOnly = true)
    public void indexContent(Long contentId) {
        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new IllegalArgumentException("콘텐츠 없음: " + contentId));
        contentSearchRepository.save(toDocument(content));
    }

    @Override
    public void deleteContent(Long contentId) {
        contentSearchRepository.deleteById(contentId);
    }

    @Override
    public Page<ContentDocument> search(String keyword, Pageable pageable) {
        if (keyword == null || keyword.isBlank()) {
            return contentSearchRepository.findAll(pageable);
        }

        // 🚨 NativeQuery: 제목^3, 태그^2 가중치 적용
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.multiMatch(m -> m
                        .fields("title^3", "tags^2", "description")
                        .query(keyword)))
                .withPageable(pageable)
                .withSort(convertSort(pageable.getSort()))
                .build();

        SearchHits<ContentDocument> hits = elasticsearchOperations.search(query, ContentDocument.class);
        List<ContentDocument> contents = hits.stream().map(h -> h.getContent()).toList();

        return new PageImpl<>(contents, pageable, hits.getTotalHits());
    }

    @Override
    public List<String> getSuggestions(String keyword) {
        // 자동완성: 제목 접두사 매칭
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.matchPhrasePrefix(m -> m.field("title").query(keyword)))
                .withPageable(Pageable.ofSize(10))
                .build();
        return elasticsearchOperations.search(query, ContentDocument.class)
                .stream().map(h -> h.getContent().getTitle()).distinct().toList();
    }

    @Override
    public long countIndexedContents() { return contentSearchRepository.count(); }

    private Sort convertSort(Sort sort) {
        return sort.isUnsorted() ? Sort.unsorted() : sort;
    }

    private ContentDocument toDocument(Content content) {
        return ContentDocument.builder()
                .contentId(content.getId())
                .title(content.getTitle())
                .description(content.getDescription())
                .tags(content.getTags().stream().map(Tag::getName).toList())
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