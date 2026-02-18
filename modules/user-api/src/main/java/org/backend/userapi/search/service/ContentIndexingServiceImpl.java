package org.backend.userapi.search.service;

import common.entity.Tag;
import content.entity.Content;
import content.repository.ContentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.backend.userapi.search.document.ContentDocument;
import org.backend.userapi.search.repository.ContentSearchRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightParameters;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContentIndexingServiceImpl implements ContentIndexingService {

    private final ContentRepository contentRepository;
    private final ContentSearchRepository contentSearchRepository;
    private final ElasticsearchOperations elasticsearchOperations;

    private final AtomicBoolean isIndexing = new AtomicBoolean(false);
    
    // 🚨 [추가] 작업 상태 모니터링용 필드
    private String lastIndexingStatus = "IDLE"; 
    private String lastErrorMessage = null;
    private LocalDateTime lastRunTime = null;

    @Override
    @Async("indexingExecutor")
    @Transactional(readOnly = true)
    public void indexAllContents() {
        if (isIndexing.getAndSet(true)) {
            log.warn("⚠️ 이미 인덱싱 작업이 진행 중입니다.");
            return;
        }

        try {
            log.info("🚀 전체 콘텐츠 인덱싱 시작...");
            lastIndexingStatus = "RUNNING";
            lastRunTime = LocalDateTime.now();
            lastErrorMessage = null;

            List<Content> contents = contentRepository.findAllWithTags();
            
            List<ContentDocument> documents = contents.stream().map(this::toDocument).toList();
            contentSearchRepository.saveAll(documents);
            
            log.info("✅ 전체 콘텐츠 인덱싱 완료 (총 {}건)", documents.size());
            lastIndexingStatus = "SUCCESS";

        } catch (Exception e) {
            log.error("❌ 인덱싱 중 치명적 오류 발생", e);
            lastIndexingStatus = "FAILED";
            lastErrorMessage = e.getMessage();
            
            throw new RuntimeException("인덱싱 작업 실패", e); 
        } finally {
            isIndexing.set(false);
        }
    }

    @Override
    public Page<ContentDocument> search(String keyword, Pageable pageable) {
    	
    	if (!StringUtils.hasText(keyword)) {
            return Page.empty(pageable);
        }

        HighlightParameters highlightParameters = HighlightParameters.builder()
                .withPreTags("<em>")
                .withPostTags("</em>")
                .build();

        HighlightField titleField = new HighlightField("title");
        HighlightField descField = new HighlightField("description");

        Highlight highlight = new Highlight(highlightParameters, List.of(titleField, descField));
        
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.multiMatch(m -> m
                        .fields("title^3", "tags^2", "description")
                        .query(keyword)))
                .withPageable(pageable)
                .withHighlightQuery(new HighlightQuery(highlight, null))
                .build();

        SearchHits<ContentDocument> searchHits = elasticsearchOperations.search(query, ContentDocument.class);

        List<ContentDocument> contents = searchHits.stream()
                .map(hit -> {
                    ContentDocument doc = hit.getContent();
                    List<String> titleHighlight = hit.getHighlightField("title");
                    if (!titleHighlight.isEmpty()) doc.setHighlightTitle(titleHighlight.get(0));
                    
                    List<String> descHighlight = hit.getHighlightField("description");
                    if (!descHighlight.isEmpty()) doc.setHighlightDescription(descHighlight.get(0));
                    return doc;
                })
                .toList();

        return new PageImpl<>(contents, pageable, searchHits.getTotalHits());
    }

    @Override
    public Map<String, Object> getIndexingStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("status", lastIndexingStatus);
        status.put("lastRunTime", lastRunTime);
        if (lastErrorMessage != null) {
            status.put("error", lastErrorMessage);
        }
        return status;
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
    public List<String> getSuggestions(String keyword) {
        if (!StringUtils.hasText(keyword)) return List.of();

        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.matchPhrasePrefix(m -> m.field("title").query(keyword)))
                .withPageable(Pageable.ofSize(10))
                .build();

        return elasticsearchOperations.search(query, ContentDocument.class)
                .stream()
                .map(h -> h.getContent().getTitle())
                .distinct()
                .toList();
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