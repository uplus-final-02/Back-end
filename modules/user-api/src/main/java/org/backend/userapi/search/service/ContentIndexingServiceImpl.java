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
import common.enums.ContentStatus;
import common.enums.ContentType;
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

    // [피드백 반영] 현재는 단일 서버 환경을 가정하여 AtomicBoolean을 사용.
    // 향후 다중 인스턴스 환경(Scale-out) 시 Redis 기반 분산 락으로 고도화 예정.
    private final AtomicBoolean isIndexing = new AtomicBoolean(false);
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper =
            new com.fasterxml.jackson.databind.ObjectMapper();

    // [피드백 반영] @Async 스레드에서 쓰고 HTTP 스레드에서 읽으므로 volatile 필수
    private volatile String lastIndexingStatus = "IDLE";
    private volatile String lastErrorMessage = null;
    private volatile LocalDateTime lastRunTime = null;

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
            log.info("🚀 전체 콘텐츠 인덱싱 시작 (초고속 커서 기반 청크 처리)...");
            lastIndexingStatus = "RUNNING";
            lastRunTime = LocalDateTime.now();
            lastErrorMessage = null;

            int pageSize = 500;
            long lastId = 0L;
            long totalIndexed = 0;

            // [피드백 반영] ids 스코프를 do 블록 밖으로 선언 (while 조건에서 참조)
            List<Long> ids;
            do {
                Pageable pageable = PageRequest.of(0, pageSize);

                // [피드백 반영] 2단계 쿼리로 N+1 + 메모리 페이징 동시 해결
                // 1단계: ID만 커서 기반으로 가져오기 (페이징은 DB에서 정확히 처리)
                ids = contentRepository.findIdsCursor(lastId, pageable);
                if (ids.isEmpty()) break;

                // 2단계: ID 목록으로 fetch join (N+1 없음, 메모리 페이징 없음)
                List<Content> contents = contentRepository.findAllWithTagsByIds(ids);

                List<ContentDocument> documents = contents.stream()
                        .map(this::toDocument)
                        .toList();

                contentSearchRepository.saveAll(documents);
                totalIndexed += documents.size();
                lastId = ids.get(ids.size() - 1);

                log.info("인덱싱 진행 중... 누적 [{}건] 완료 (현재 커서 ID: {})", totalIndexed, lastId);

            } while (ids.size() == pageSize);

            log.info("✅ 전체 콘텐츠 초고속 인덱싱 완료 (총 {}건)", totalIndexed);
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

        // 1. 유저 선호 태그 벡터 준비 (람다식 내부에서 쓰기 위해 미리 변환)
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
                    log.warn("[검색] userId: {} 의 취향 벡터가 0-벡터로 판명되어 kNN 검색을 제외합니다.", userId);
                }
            }
        }

        final List<Float> finalVectorList = vectorList;

        // 2. 하이라이트 설정
        HighlightParameters hp = HighlightParameters.builder()
                .withPreTags("<em>").withPostTags("</em>").build();
        Highlight highlight = new Highlight(hp, List.of(
                new HighlightField("title"),
                new HighlightField("title.ngram"),
                new HighlightField("description"),
                new HighlightField("titleChosung"),
                new HighlightField("titleChosung.ngram")
        ));

        // 3. 동적 Bool 쿼리 조합
        NativeQueryBuilder queryBuilder = NativeQuery.builder()
                .withQuery(q -> q.functionScore(fs -> fs
                        .query(innerQ -> innerQ.bool(b -> {
                            if (StringUtils.hasText(keyword)) {
                                boolean isChosungQuery = !keyword.matches(".*[가-힣].*");

                                b.must(m -> m.bool(bool -> {
                                    bool.should(s -> s.multiMatch(mm -> mm
                                            .fields("title.ngram^5", "title^15", "tags^3", "tags.search^2", "description^2")
                                            .query(keyword)
                                            .operator(co.elastic.clients.elasticsearch._types.query_dsl.Operator.And)
                                    ));

                                    // 1순위: 제목 완전 일치 (200점)
                                    bool.should(s -> s.matchPhrase(mp -> mp
                                            .field("title")
                                            .query(keyword)
                                            .boost(200.0f)
                                    ));

                                    // 2순위: 제목 부분 일치 N-gram (80점)
                                    bool.should(s -> s.matchPhrase(mp -> mp
                                            .field("title.ngram")
                                            .query(keyword)
                                            .boost(80.0f)
                                    ));

                                    // 3순위: 제목 오타 허용 교정 검색 (50점)
                                    bool.should(s -> s.match(ma -> ma
                                            .field("title")
                                            .query(keyword)
                                            .fuzziness("AUTO")
                                            .boost(50.0f)
                                    ));

                                    bool.should(s -> s.multiMatch(mm -> mm
                                            .fields("title.ngram^5", "tags^3", "tags.search^2", "description^1")
                                            .query(keyword)
                                            .operator(co.elastic.clients.elasticsearch._types.query_dsl.Operator.And)
                                    ));

                                    bool.should(s -> s.matchPhrasePrefix(mpp -> mpp
                                            .field("description")
                                            .query(keyword)
                                            .boost(0.5f)
                                    ));

                                    if (isChosungQuery) {
                                        String chosungKeyword = getChosungKeyword(keyword);
                                        bool.should(s -> s.multiMatch(mm -> mm
                                                .fields("titleChosung^5", "titleChosung.ngram^3")
                                                .query(chosungKeyword)
                                                .operator(co.elastic.clients.elasticsearch._types.query_dsl.Operator.And)
                                        ));
                                    }

                                    bool.minimumShouldMatch("1");
                                    return bool;
                                }));
                            }

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
                                .missing(1.0)
                        ))
                        .functions(fn -> fn.fieldValueFactor(fvf -> fvf
                                .field("bookmarkCount")
                                .modifier(FieldValueFactorModifier.Log1p)
                                .factor(0.2)
                                .missing(1.0)
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

                        // 일반 제목 하이라이트 우선, 없으면 초성 하이라이트로 대체
                        String hl = hit.getHighlightField("title").stream().findFirst()
                                .orElseGet(() -> hit.getHighlightField("titleChosung.ngram")
                                        .stream().findFirst()
                                        .orElse(null));

                        doc.setHighlightTitle(hl);
                        doc.setHighlightDescription(
                                hit.getHighlightField("description").stream().findFirst().orElse(null));
                        return doc;
                    })
                    .toList();

            return new PageImpl<>(contents, pageable, searchHits.getTotalHits());

        } catch (DataAccessException e) {
            log.warn("[ES DOWN] 검색 ES 연결 실패 → DB LIKE Fallback: keyword={}, category={}, genre={}, tag={}",
                    keyword, category, genre, tag);
            return searchFromDb(keyword, category, genre, tag, pageable);
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
    public long countIndexedContents() {
        return contentSearchRepository.count();
    }

    // Description JSON 파싱 로직 공통 메서드 (안전한 반환 보장)
    private String parseDescription(String rawDescription) {
        if (!StringUtils.hasText(rawDescription)) return rawDescription;
        try {
            com.fasterxml.jackson.databind.JsonNode rootNode = objectMapper.readTree(rawDescription);
            if (rootNode.has("summary")) {
                return rootNode.get("summary").asText();
            }
        } catch (Exception e) {
            // 일반 텍스트면 원본 그대로 반환
        }
        return rawDescription;
    }

    // 초성 추출 로직 공통 메서드
    private String getChosungKeyword(String keyword) {
        return ChosungUtil.extract(keyword).replaceAll("\\s+", "");
    }

    private ContentDocument toDocument(Content content) {
        List<Long> tagIds = content.getContentTags().stream()
                .map(ct -> ct.getTag().getId())
                .collect(Collectors.toList());

        float[] tagVector = tagVectorService.buildContentVector(tagIds);

        return toSimpleDocument(content).toBuilder()
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

    // ── ES 검색 Fallback: DB LIKE ─────────────────────────────────────────
    private Page<ContentDocument> searchFromDb(String keyword, String category, String genre, String tag, Pageable pageable) {
        try {
            Pageable dbPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());

            ContentType categoryType = null;
            if (StringUtils.hasText(category)) {
                try {
                    categoryType = ContentType.valueOf(category.toUpperCase());
                } catch (IllegalArgumentException ignored) {
                    log.warn("[ES Fallback] 알 수 없는 category 값: {} → 필터 없이 진행", category);
                }
            }

            String genreFilter = StringUtils.hasText(genre) ? genre : null;
            String tagFilter   = StringUtils.hasText(tag)   ? tag   : null;

            List<Content> contents;
            long total;

            if (StringUtils.hasText(keyword)) {
                boolean isLatest = pageable.getSort().stream()
                        .anyMatch(order -> order.getProperty().equals("createdAt")
                                && order.isDescending());

                // [피드백 반영] RELATED는 DB에서 지원 불가 → POPULAR로 대체, 명시적 로그
                boolean isRelated = pageable.getSort().stream()
                        .anyMatch(order -> order.getProperty().equals("_score"));
                if (isRelated) {
                    log.warn("[ES Fallback] RELATED 정렬은 DB에서 지원 불가 → POPULAR(인기순)로 대체 처리");
                }

                if (isLatest) {
                    contents = contentRepository.findActiveByTitleLikeLatest(
                            keyword, categoryType, genreFilter, tagFilter,
                            ContentStatus.ACTIVE, dbPageable);
                } else {
                    contents = contentRepository.findActiveByTitleLikePopular(
                            keyword, categoryType, genreFilter, tagFilter,
                            ContentStatus.ACTIVE, dbPageable);
                }
                total = contentRepository.countActiveByTitleLike(
                        keyword, categoryType, genreFilter, tagFilter,
                        ContentStatus.ACTIVE);
            } else {
                contents = contentRepository.findTopActiveByPopularity(
                        ContentStatus.ACTIVE, dbPageable);
                total = contentRepository.countAllActive();
            }

            List<ContentDocument> docs = contents.stream()
                    .map(this::toSimpleDocument)
                    .toList();

            log.info("[ES Fallback] DB LIKE 검색 결과: keyword={}, category={}, genre={}, tag={}, 결과={}건, total={}건",
                    keyword, category, genre, tag, docs.size(), total);
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

        String rawChosung = ChosungUtil.extract(content.getTitle());
        String noSpaceChosung = rawChosung != null ? rawChosung.replaceAll("\\s+", "") : "";
        String combinedChosung = (rawChosung != null ? rawChosung : "") + " " + noSpaceChosung;

        return ContentDocument.builder()
                .contentId(content.getId())
                .title(content.getTitle())
                .titleChosung(combinedChosung)
                .description(parseDescription(content.getDescription()))
                .tags(tagNames)
                .contenttype(content.getType().name())
                .status(content.getStatus().name())
                .accessLevel(content.getAccessLevel().name())
                .thumbnailUrl(content.getThumbnailUrl())
                .totalViewCount(content.getTotalViewCount())
                .bookmarkCount(content.getBookmarkCount())
                .createdAt(content.getCreatedAt())
                .updatedAt(content.getUpdatedAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ContentDocument> getAlternativeContents(Pageable pageable) {
        log.info("[OTT 대체 추천] 검색 결과가 없어 전체 인기작(Fallback)을 제공합니다.");
        return searchFromDb(null, null, null, null, pageable);
    }
}