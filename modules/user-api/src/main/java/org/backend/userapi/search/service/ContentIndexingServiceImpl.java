package org.backend.userapi.search.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.backend.userapi.recommendation.service.TagVectorService;
import org.backend.userapi.search.document.ContentDocument;
import org.backend.userapi.search.repository.ContentSearchRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
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

import co.elastic.clients.elasticsearch._types.query_dsl.FieldValueFactorModifier;
import co.elastic.clients.elasticsearch._types.query_dsl.FunctionBoostMode;
import content.entity.Content;
import content.repository.ContentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import user.repository.UserPreferredTagRepository;


@Slf4j
@Service
@RequiredArgsConstructor
public class ContentIndexingServiceImpl implements ContentIndexingService {

    private final ContentRepository contentRepository;
    private final ContentSearchRepository contentSearchRepository;
    private final ElasticsearchOperations elasticsearchOperations;
    private final TagVectorService tagVectorService;
    private final UserPreferredTagRepository userPreferredTagRepository;

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
    public Page<ContentDocument> search(String keyword, String category, String genre, String tag, Long userId, Pageable pageable) {
        
        if (!StringUtils.hasText(keyword) && !StringUtils.hasText(category) && 
                !StringUtils.hasText(genre) && !StringUtils.hasText(tag)) {
                log.info("검색어와 필터가 모두 비어있어 빈 결과를 반환합니다.");
                return new PageImpl<>(List.of(), pageable, 0); 
        }

        // 💡 1. 유저 선호 태그 벡터 준비 (람다식 내부에서 쓰기 위해 미리 변환)
        List<Float> vectorList = null;
        if (userId != null && StringUtils.hasText(keyword)) { 
            List<Long> preferredTagIds = userPreferredTagRepository.findAllByUserIdWithTag(userId)
                    .stream()
                    .map(upt -> upt.getTag().getId())
                    .toList();

            if (!preferredTagIds.isEmpty()) {
                float[] userVector = tagVectorService.buildUserVector(preferredTagIds);
                vectorList = new ArrayList<>();
                for (float v : userVector) {
                    vectorList.add(v);
                }
            }
        }
        
        final List<Float> finalVectorList = vectorList; // 람다 내부 접근용 final 변수
        
        // 2. 하이라이트 설정
        HighlightParameters hp = HighlightParameters.builder()
                .withPreTags("<em>").withPostTags("</em>").build();
        Highlight highlight = new Highlight(hp, List.of(new HighlightField("title"), new HighlightField("description")));

        // 3. 동적 Bool 쿼리 조합
        NativeQueryBuilder queryBuilder = NativeQuery.builder()
                .withQuery(q -> q.functionScore(fs -> fs
                        // 1️⃣ 기존 조건 유지 (키워드, 필터, 벡터Should)
                        .query(innerQ -> innerQ.bool(b -> {
                            // [기본] 키워드 검색
                            if (StringUtils.hasText(keyword)) {
                                b.must(m -> m.multiMatch(mm -> mm
                                        .fields("title^3", "tags^2", "description")
                                        .query(keyword)));
                            }

                            // [필터] 조건들
                            if (StringUtils.hasText(category)) {
                                b.filter(f -> f.term(t -> t.field("contenttype").value(category.toUpperCase())));
                            }
                            if (StringUtils.hasText(genre)) {
                                b.filter(f -> f.term(t -> t.field("tags").value(genre)));
                            }
                            if (StringUtils.hasText(tag)) {
                                b.filter(f -> f.term(t -> t.field("tags").value(tag)));
                            }
                            b.filter(f -> f.term(t -> t.field("status").value("ACTIVE")));
                            
                            // 💡 하이브리드 벡터 (Should)
                            if (finalVectorList != null) {
                                b.should(s -> s.knn(knn -> knn
                                        .field("tagVector")
                                        .queryVector(finalVectorList)
                                        .k(30)
                                        .numCandidates(100)
                                        .boost(0.5f) 
                                ));
                            }
                            return b;
                        }))
                        .functions(fn -> fn
                                .fieldValueFactor(fvf -> fvf
                                        .field("totalViewCount")
                                        .modifier(FieldValueFactorModifier.Log1p)
                                        .factor(0.1) // 텍스트 검색을 너무 덮지 않도록 0.1 부여
                                )
                        )
                        .functions(fn -> fn
                                .fieldValueFactor(fvf -> fvf
                                        .field("bookmarkCount")
                                        .modifier(FieldValueFactorModifier.Log1p)
                                        .factor(0.2)
                                )
                        )
                        // 3️⃣ 최종 점수 합산 방식
                        .boostMode(FunctionBoostMode.Sum)
                ))
                .withPageable(pageable)
                .withHighlightQuery(new HighlightQuery(highlight, null));

        if (finalVectorList != null) {
            log.info("🚀 [하이브리드 검색 가동] userId: {}, 키워드: {}", userId, keyword);
        }

        // 4. 결과 처리 및 하이라이트 매핑
        NativeQuery query = queryBuilder.build();
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

        List<Long> tagIds = content.getContentTags().stream()
                .map(ct -> ct.getTag().getId())
                .collect(Collectors.toList());

        // priority 가중치가 반영된 30차원 태그 벡터 빌드
        float[] tagVector = tagVectorService.buildContentVector(tagIds);

        return ContentDocument.builder()
                .contentId(content.getId())
                .title(content.getTitle())
                .description(content.getDescription())
                .tags(tagNames)
                .contenttype(content.getType().name())
                .status(content.getStatus().name())
                .accessLevel(content.getAccessLevel().name())
                .thumbnailUrl(content.getThumbnailUrl())
                .totalViewCount(content.getTotalViewCount())
                .bookmarkCount(content.getBookmarkCount())
                .createdAt(content.getCreatedAt())
                .updatedAt(content.getUpdatedAt())
                .tagVector(tagVector)
                .build();
    }
}