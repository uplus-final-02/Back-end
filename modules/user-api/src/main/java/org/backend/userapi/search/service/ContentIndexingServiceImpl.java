package org.backend.userapi.search.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.backend.userapi.search.document.ContentDocument;
import org.backend.userapi.search.repository.ContentSearchRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightParameters;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import content.entity.Content;
import content.repository.ContentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContentIndexingServiceImpl implements ContentIndexingService {

    private final ContentRepository contentRepository;
    private final ContentSearchRepository contentSearchRepository;
    private final ElasticsearchOperations elasticsearchOperations;

    private final AtomicBoolean isIndexing = new AtomicBoolean(false);
    
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
            
            List<ContentDocument> documents = contents.stream()
                    .map(this::toDocument)
                    .toList();

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
    public Page<ContentDocument> search(String keyword, String category, String genre, String tag, Pageable pageable) {
    	
    	if (!StringUtils.hasText(keyword) && !StringUtils.hasText(category) && 
                !StringUtils.hasText(genre) && !StringUtils.hasText(tag)) {
                log.info("검색어와 필터가 모두 비어있어 빈 결과를 반환합니다.");
                return new PageImpl<>(List.of(), pageable, 0); 
            }
    	
        // 1. 하이라이트 설정 보존
    	HighlightParameters hp = HighlightParameters.builder()
                .withPreTags("<em>").withPostTags("</em>").build();
        Highlight highlight = new Highlight(hp, List.of(new HighlightField("title"), new HighlightField("description")));

        // 2. 동적 Bool 쿼리 (Must: 키워드 가중치 / Filter: 카테고리, 태그)
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> {
                    // 키워드 검색 (점수 반영)
                    if (StringUtils.hasText(keyword)) {
                        b.must(m -> m.multiMatch(mm -> mm
                                .fields("title^3", "tags^2", "description")
                                .query(keyword)));
                    }

                    // 카테고리 필터 (정확도 점수 미반영, 캐싱 활용)
                    if (StringUtils.hasText(category)) {
                        b.filter(f -> f.term(t -> t.field("category").value(category.toUpperCase())));
                    }

                    // 장르/태그 필터 (태그 리스트 내 검색)
                    if (StringUtils.hasText(genre)) {
                        b.filter(f -> f.term(t -> t.field("tags").value(genre)));
                    }
                    if (StringUtils.hasText(tag)) {
                        b.filter(f -> f.term(t -> t.field("tags").value(tag)));
                    }

                    // 활성 콘텐츠만 검색 (공통 필터)
                    b.filter(f -> f.term(t -> t.field("status").value("ACTIVE")));
                    
                    return b;
                }))
                .withPageable(pageable)
                .withHighlightQuery(new HighlightQuery(highlight, null))
                .build();

        // 3. 결과 처리 및 하이라이트 매핑
        SearchHits<ContentDocument> searchHits = elasticsearchOperations.search(query, ContentDocument.class);

        List<ContentDocument> contents = searchHits.stream()
                .map(hit -> {
                    ContentDocument doc = hit.getContent();
                    doc.setHighlightTitle(hit.getHighlightField("title").stream().findFirst().orElse(null));
                    doc.setHighlightDescription(hit.getHighlightField("description").stream().findFirst().orElse(null));
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
        List<String> tagNames = content.getContentTags().stream()
                .map(ct -> ct.getTag().getName())
                .collect(Collectors.toList());

        return ContentDocument.builder()
                .contentId(content.getId())
                .title(content.getTitle())
                .description(content.getDescription())
                .tags(tagNames) // 수정된 태그 리스트 주입
                .category(content.getType().name())
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