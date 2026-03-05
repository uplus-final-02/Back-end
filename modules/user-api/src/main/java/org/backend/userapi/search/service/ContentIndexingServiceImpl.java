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
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
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
import co.elastic.clients.elasticsearch._types.query_dsl.FunctionScoreMode;
import common.util.ChosungUtil;
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
    
    private static final float ZERO_VECTOR_EPS = 1e-6f;

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
                
                if (!isZeroVector(userVector)) {
                    vectorList = new ArrayList<>();
                    for (float v : userVector) {
                        vectorList.add(v);
                    }
                } else {
                    log.warn("🚀 [검색] userId: {} 의 취향 벡터가 0-벡터로 판명되어 kNN 검색을 제외합니다.", userId);
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
                        .query(innerQ -> innerQ.bool(b -> {
                            // [기본] 키워드 검색
                        	if (StringUtils.hasText(keyword)) {
                                
                                boolean isChosungQuery = !keyword.matches(".*[가-힣].*");
                                
                                b.must(m -> m.bool(bool -> {
                                    bool.should(s -> s.multiMatch(mm -> mm
                                            .fields("title^5", "title.ngram^3", "tags^3", "description^2")
                                            .query(keyword)
                                    ));

                                    bool.should(s -> s.matchPhrasePrefix(mpp -> mpp
                                            .field("description")
                                            .query(keyword)
                                            .boost(1.5f) 
                                    ));

                                    if (isChosungQuery) {
                                        String chosungKeyword = ChosungUtil.extract(keyword);
                                        bool.should(s -> s.multiMatch(mm -> mm
                                                .fields("titleChosung^2", "titleChosung.ngram^1")
                                                .query(chosungKeyword)
                                        ));
                                    }

                                    bool.minimumShouldMatch("1");
                                    return bool;
                                }));
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
                            
                            // [하이브리드 kNN 검색] (유효한 벡터일 때만 동작)
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
                        .functions(fn -> fn.fieldValueFactor(fvf -> fvf
                                .field("totalViewCount")
                                .modifier(FieldValueFactorModifier.Log1p)
                                .factor(0.1) 
                                .missing(0.0)
                        ))
                        .functions(fn -> fn.fieldValueFactor(fvf -> fvf
                                .field("bookmarkCount")
                                .modifier(FieldValueFactorModifier.Log1p)
                                .factor(0.2) 
                                .missing(0.0)
                        ))
                        .scoreMode(FunctionScoreMode.Sum)
                        .boostMode(FunctionBoostMode.Sum) 
                ))
                .withPageable(pageable)
                .withHighlightQuery(new HighlightQuery(highlight, null));

        if (finalVectorList != null) {
            log.info("🚀 [하이브리드 검색 가동] userId: {}, 키워드: {}", userId, keyword);
        }

        // 4. 결과 처리 및 하이라이트 매핑
        NativeQuery query = queryBuilder.build();
        try {
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

        } catch (DataAccessException e) {
            log.warn("[ES DOWN] 검색 ES 연결 실패 → DB LIKE Fallback: keyword={}, category={}", keyword, category);
            return searchFromDb(keyword, category, pageable);
        }
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

        boolean isChosungQuery = !keyword.matches(".*[가-힣].*");

        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> {
                    b.should(s -> s.match(m -> m.field("title.ngram").query(keyword)));

                    if (isChosungQuery) {
                        String chosungKeyword = ChosungUtil.extract(keyword);
                        b.should(s -> s.match(m -> m.field("titleChosung.ngram").query(chosungKeyword)));
                    }

                    b.minimumShouldMatch("1");
                    return b;
                }))
                .withPageable(Pageable.ofSize(10))
                .build();

        try {
            return elasticsearchOperations.search(query, ContentDocument.class)
                    .stream()
                    .map(h -> h.getContent().getTitle())
                    .distinct()
                    .toList();
        } catch (DataAccessException e) {
            log.warn("[ES DOWN] 자동완성 ES 연결 실패 → 빈 결과 반환: keyword={}", keyword);
            return List.of(); // 자동완성은 빈 결과가 500보다 UX에 자연스러움
        }
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
                .titleChosung(ChosungUtil.extract(content.getTitle()))
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
    
    private boolean isZeroVector(float[] vector) {
        if (vector == null || vector.length == 0) return true;
        double normSq = 0.0;
        for (float v : vector) {
            normSq += (double) v * v;
        }
        return normSq <= ZERO_VECTOR_EPS;
    }

    // ── ES 검색 Fallback: DB LIKE ────────────────────────────────────────
    private Page<ContentDocument> searchFromDb(String keyword, String category, Pageable pageable) {
        try {
            Pageable dbPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());

            // category 문자열 → ContentType enum 변환 (null-safe)
            common.enums.ContentType categoryType = null;
            if (StringUtils.hasText(category)) {
                try {
                    categoryType = common.enums.ContentType.valueOf(category.toUpperCase());
                } catch (IllegalArgumentException ignored) {
                    log.warn("[ES Fallback] 알 수 없는 category 값: {} → 필터 없이 진행", category);
                }
            }

            List<Content> contents;
            long total;

            if (StringUtils.hasText(keyword)) {
                // 정렬 결정: pageable sort에 createdAt 포함 시 LATEST, 그 외 POPULAR(RELATED 포함)
                boolean isLatest = pageable.getSort().stream()
                        .anyMatch(order -> order.getProperty().equals("createdAt"));

                if (isLatest) {
                    contents = contentRepository.findActiveByTitleLikeLatest(keyword, categoryType, dbPageable);
                } else {
                    contents = contentRepository.findActiveByTitleLikePopular(keyword, categoryType, dbPageable);
                }
                total = contentRepository.countActiveByTitleLike(keyword, categoryType);
            } else {
                // keyword 없음(필터만): category 무시하고 인기순 반환 (Fallback 상황 허용 수준)
                contents = contentRepository.findTopActiveByPopularity(dbPageable);
                total = contentRepository.countAllActive(); // 정확한 total로 hasNext 보장
            }

            List<ContentDocument> docs = contents.stream()
                    .map(this::toSimpleDocument)
                    .toList();

            log.info("[ES Fallback] DB LIKE 검색 결과: keyword={}, category={}, 결과={}건, total={}건",
                    keyword, category, docs.size(), total);
            return new PageImpl<>(docs, pageable, total);

        } catch (Exception dbEx) {
            log.error("[ES+DB DOWN] DB Fallback도 실패 → 빈 결과 반환: {}", dbEx.getMessage());
            return new PageImpl<>(List.of(), pageable, 0);
        }
    }

    // ── 벡터 없는 간소화 문서 변환 (Fallback 전용) ───────────────────────
    private ContentDocument toSimpleDocument(Content content) {
        List<String> tagNames = content.getContentTags().stream()
                .map(ct -> ct.getTag().getName())
                .collect(Collectors.toList());
        return ContentDocument.builder()
                .contentId(content.getId())
                .title(content.getTitle())
                .titleChosung(ChosungUtil.extract(content.getTitle()))
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
                .build(); // tagVector 생략 (벡터 연산 불필요)
    }
}