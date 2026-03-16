package org.backend.userapi.recommendation.service;

import content.entity.UserContent;
import content.repository.UserContentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.backend.userapi.recommendation.dto.UserFeedResponse;
import org.backend.userapi.recommendation.dto.UserRecommendationResponse;
import org.backend.userapi.recommendation.dto.UserRecommendedContentResponse;
import org.backend.userapi.search.document.UserContentDocument;
import org.backend.userapi.search.repository.UserContentSearchRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import user.repository.UserPreferredTagRepository;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 유저 업로드 콘텐츠 HNSW 기반 2-Stage 개인화 추천 서비스.
 *
 * <p>기존 {@link RecommendationService}(관리자 콘텐츠)와 동일한 알고리즘 구조를 사용하며,
 * 아래 항목만 다르다:
 * <ul>
 *   <li>ES 인덱스: {@code user_contents_v1} (관리자: {@code contents_v1})</li>
 *   <li>ACTIVE 필터 필드: {@code contentStatus} (관리자: {@code status})</li>
 *   <li>시청 이력 패널티: 미적용 (UserContent WatchHistory 미구현)</li>
 *   <li>DB Fallback: {@link UserContentRepository}</li>
 * </ul>
 *
 * <pre>
 * Stage 1 (ES / HNSW kNN)
 *   유저 선호 태그 → 100차원 쿼리 벡터 → 코사인 유사도 kNN
 *   ACTIVE 유저 콘텐츠 전체의 1/3 후보 동적 추출
 *
 * Stage 2 (내부 정밀 랭킹)
 *   finalScore = W_SIMILARITY  × tagSimilarity   (0.60)
 *              + W_POPULARITY  × popularityScore  (0.25)
 *              + W_FRESHNESS   × freshnessScore   (0.15)
 *   ※ watchPenalty 없음 (시청 이력 미구현)
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserRecommendationService {

    private final UserPreferredTagRepository userPreferredTagRepository;
    private final TagVectorService tagVectorService;
    private final ElasticsearchOperations elasticsearchOperations;
    private final UserContentRepository userContentRepository;
    private final UserContentSearchRepository userContentSearchRepository;

    // ── Stage 1 후보 크기 ─────────────────────────────────────────
    private static final int MIN_CANDIDATE_SIZE = 30;
    private static final int MAX_CANDIDATE_SIZE = 1_000;

    // ── Stage 2 결과 크기 ─────────────────────────────────────────
    private static final int RESULT_SIZE  = 50;
    private static final int INITIAL_SIZE = 15;

    // ── 점수 가중치 ───────────────────────────────────────────────
    private static final double W_SIMILARITY  = 0.60;
    private static final double W_POPULARITY  = 0.25;
    private static final double W_FRESHNESS   = 0.15;

    // ── Fallback 가중치 (0-벡터 시) ──────────────────────────────
    private static final double W_POPULARITY_FALLBACK = 0.60;
    private static final double W_FRESHNESS_FALLBACK  = 0.40;

    // ── 신선도 감쇠 반감기 ────────────────────────────────────────
    private static final double FRESHNESS_DECAY_DAYS = 365.0;

    // ── 0-벡터 방어 ──────────────────────────────────────────────
    private static final float ZERO_VECTOR_EPS = 1e-6f;

    // ── 내부 집계용 임시 레코드 ───────────────────────────────────
    private record RawCandidate(
            UserContentDocument doc,
            double similarity,
            double popularityRaw,
            double freshnessScore
    ) {}

    // =========================================================
    //  Public API
    // =========================================================

    /**
     * 유저 업로드 콘텐츠 개인화 추천.
     *
     * @param userId   현재 로그인 유저 ID
     * @param extended false → 15개 기본 / true → 50개 "더 알아보기"
     */
    @Transactional(readOnly = true)
    public UserRecommendationResponse recommend(Long userId, boolean extended) {
        try {
            // 1. 유저 선호 태그 조회
            List<Long> preferredTagIds = userPreferredTagRepository
                    .findAllByUserIdWithTag(userId)
                    .stream()
                    .map(upt -> upt.getTag().getId())
                    .toList();

            if (preferredTagIds.isEmpty()) {
                log.info("[유저콘텐츠 추천] userId={} 선호 태그 없음 → 빈 결과 반환", userId);
                return new UserRecommendationResponse(List.of(), false);
            }

            // 2. 100차원 유저 쿼리 벡터 생성
            float[] queryVector = tagVectorService.buildUserVector(preferredTagIds);

            // 3. 0-벡터 방어
            if (isZeroVector(queryVector)) {
                log.info("[유저콘텐츠 추천] userId={} 0-벡터 감지 → Fallback 모드", userId);
                return fallbackRecommend(userId, extended);
            }

            // 4. 동적 후보 크기 계산 (전체 ACTIVE의 1/3)
            long totalActive  = countActiveUserContents();
            int candidateSize = calcCandidateSize(totalActive);
            log.info("[유저콘텐츠 추천] userId={} 총 활성 유저콘텐츠={} → Stage1 후보 목표={}",
                    userId, totalActive, candidateSize);

            // 5. Stage 1: ES kNN
            List<SearchHit<UserContentDocument>> candidates = knnSearch(queryVector, candidateSize);
            if (candidates.isEmpty()) {
                log.info("[유저콘텐츠 추천] userId={} kNN 결과 없음 → 빈 결과 반환", userId);
                return new UserRecommendationResponse(List.of(), false);
            }

            // 6. Stage 2: 2-pass 내부 랭킹
            List<UserRecommendedContentResponse> ranked = rank(candidates);

            // 7. 2-tier 응답
            if (extended) {
                log.info("[유저콘텐츠 추천] userId={} extended → {}개 반환", userId, ranked.size());
                return new UserRecommendationResponse(ranked, false);
            } else {
                List<UserRecommendedContentResponse> initial =
                        ranked.subList(0, Math.min(INITIAL_SIZE, ranked.size()));
                boolean hasMore = ranked.size() > INITIAL_SIZE;
                log.info("[유저콘텐츠 추천] userId={} initial → {}개 반환, hasMore={}", userId, initial.size(), hasMore);
                return new UserRecommendationResponse(initial, hasMore);
            }

        } catch (DataAccessException e) {
            log.warn("[유저콘텐츠 추천 ES DOWN] ES 연결 실패 → DB 인기순 Fallback: userId={}", userId);
            return recommendFromDb(userId, extended);
        }
    }

    // =========================================================
    //  Public API — Shorts 스타일 무한스크롤 피드
    // =========================================================

    /**
     * 유튜브 숏츠 스타일 무한스크롤 피드.
     *
     * <pre>
     * [첫 진입] seedId = null
     *   → 유저 선호 태그 벡터로 kNN 검색
     *
     * [다음 스크롤] seedId = 마지막으로 본 userContentId
     *   → 해당 콘텐츠의 tagVector로 kNN → 유사한 콘텐츠 계속 등장
     *
     * [excludeIds] 이미 본 콘텐츠 ID 목록 (클라이언트가 누적 전달)
     *   → ES filter must_not 으로 서버 사이드 제외
     * </pre>
     *
     * @param userId     현재 로그인 유저 ID
     * @param seedId     마지막으로 본 userContentId (첫 진입 시 null)
     * @param size       한 번에 반환할 개수 (기본 10)
     * @param excludeIds 이미 본 콘텐츠 ID 목록
     */
    @Transactional(readOnly = true)
    public UserFeedResponse feed(Long userId, Long seedId, int size, List<Long> excludeIds) {
        try {
            float[] queryVector = resolveQueryVector(userId, seedId);

            if (isZeroVector(queryVector)) {
                log.info("[유저콘텐츠 피드] userId={} 0-벡터 → 인기순 Fallback", userId);
                return fallbackFeed(size);
            }

            // excludeIds를 포함해 size보다 여유 있게 후보 추출
            int candidateSize = Math.max(size * 3, MIN_CANDIDATE_SIZE);
            List<SearchHit<UserContentDocument>> candidates =
                    knnSearchWithExclusion(queryVector, candidateSize, excludeIds);

            if (candidates.isEmpty()) {
                return new UserFeedResponse(List.of(), null, false);
            }

            // 상위 size개 랭킹
            List<UserRecommendedContentResponse> items = rankTopN(candidates, size);

            Long nextSeedId = items.isEmpty() ? null : items.get(items.size() - 1).userContentId();
            // 반환한 batch가 요청 size를 꽉 채웠으면 다음 페이지가 있다고 판단
            boolean hasMore = items.size() == size;

            log.info("[유저콘텐츠 피드] userId={}, seedId={} → {}개 반환, nextSeedId={}",
                    userId, seedId, items.size(), nextSeedId);

            return new UserFeedResponse(items, nextSeedId, hasMore);

        } catch (DataAccessException e) {
            log.warn("[유저콘텐츠 피드 ES DOWN] userId={}: {}", userId, e.getMessage());
            return new UserFeedResponse(List.of(), null, false);
        }
    }

    /**
     * 쿼리 벡터 결정.
     * seedId 있으면 해당 문서의 tagVector, 없으면 유저 선호 태그 벡터 사용.
     */
    private float[] resolveQueryVector(Long userId, Long seedId) {
        if (seedId != null) {
            return userContentSearchRepository.findById(seedId)
                    .map(UserContentDocument::getTagVector)
                    .orElseGet(() -> buildUserPreferenceVector(userId));
        }
        return buildUserPreferenceVector(userId);
    }

    private float[] buildUserPreferenceVector(Long userId) {
        List<Long> preferredTagIds = userPreferredTagRepository
                .findAllByUserIdWithTag(userId)
                .stream()
                .map(upt -> upt.getTag().getId())
                .toList();

        if (preferredTagIds.isEmpty()) return new float[TagVectorService.MAX_VECTOR_DIMS];
        return tagVectorService.buildUserVector(preferredTagIds);
    }

    /**
     * excludeIds를 ES filter must_not으로 처리하는 kNN 검색.
     */
    private List<SearchHit<UserContentDocument>> knnSearchWithExclusion(
            float[] queryVector, int k, List<Long> excludeIds) {

        List<Float> queryVectorList = new ArrayList<>();
        for (float v : queryVector) queryVectorList.add(v);

        // excludeIds를 FieldValue 리스트로 변환
        List<co.elastic.clients.elasticsearch._types.FieldValue> excludeValues = (excludeIds == null || excludeIds.isEmpty())
                ? List.of()
                : excludeIds.stream()
                        .map(id -> co.elastic.clients.elasticsearch._types.FieldValue.of(id))
                        .toList();

        final boolean hasExcludes = !excludeValues.isEmpty();

        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.knn(knn -> knn
                        .field("tagVector")
                        .queryVector(queryVectorList)
                        .numCandidates(k * 2)
                        .k(k)
                        .filter(f -> f.bool(b -> {
                            b.must(m -> m.term(t -> t.field("contentStatus").value("ACTIVE")));
                            if (hasExcludes) {
                                b.mustNot(mn -> mn.terms(t -> t
                                        .field("userContentId")
                                        .terms(tv -> tv.value(excludeValues))
                                ));
                            }
                            return b;
                        }))
                ))
                .withPageable(Pageable.ofSize(k))
                .build();

        SearchHits<UserContentDocument> hits =
                elasticsearchOperations.search(query, UserContentDocument.class);
        return hits.getSearchHits();
    }

    /**
     * 후보에서 상위 N개 랭킹 (인기도 + 신선도).
     */
    private List<UserRecommendedContentResponse> rankTopN(
            List<SearchHit<UserContentDocument>> candidates, int size) {

        List<RawCandidate> rawList = candidates.stream()
                .map(this::toRawCandidate)
                .toList();

        double maxPopularity = rawList.stream()
                .mapToDouble(RawCandidate::popularityRaw)
                .max().orElse(1.0);
        if (maxPopularity == 0.0) maxPopularity = 1.0;

        final double maxPop = maxPopularity;
        return rawList.stream()
                .sorted(Comparator.comparingDouble(
                        (RawCandidate c) -> computeFinalScore(c, maxPop)
                ).reversed())
                .limit(size)
                .map(c -> UserRecommendedContentResponse.from(c.doc()))
                .toList();
    }

    // =========================================================
    //  Stage 1 — ES kNN
    // =========================================================

    private long countActiveUserContents() {
        NativeQuery countQuery = NativeQuery.builder()
                .withQuery(q -> q.term(t -> t.field("contentStatus").value("ACTIVE")))
                .withPageable(Pageable.ofSize(1))
                .build();
        return elasticsearchOperations.count(countQuery, UserContentDocument.class);
    }

    private int calcCandidateSize(long totalActive) {
        if (totalActive == 0) return MIN_CANDIDATE_SIZE;
        int size = (int) (totalActive / 3);
        return Math.min(Math.max(size, MIN_CANDIDATE_SIZE), MAX_CANDIDATE_SIZE);
    }

    private List<SearchHit<UserContentDocument>> knnSearch(float[] queryVector, int k) {
        List<Float> queryVectorList = new ArrayList<>();
        for (float v : queryVector) queryVectorList.add(v);

        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.knn(knn -> knn
                        .field("tagVector")
                        .queryVector(queryVectorList)
                        .numCandidates(k * 2)
                        .k(k)
                        .filter(f -> f.term(t -> t
                                .field("contentStatus")
                                .value("ACTIVE")))
                ))
                .withPageable(Pageable.ofSize(k))
                .build();

        SearchHits<UserContentDocument> hits =
                elasticsearchOperations.search(query, UserContentDocument.class);
        log.info("[유저콘텐츠 추천] Stage 1 완료 — kNN 후보 {}개 추출 (k={})", hits.getTotalHits(), k);
        return hits.getSearchHits();
    }

    // =========================================================
    //  Stage 2 — 내부 랭킹 (시청 이력 패널티 없음)
    // =========================================================

    private List<UserRecommendedContentResponse> rank(
            List<SearchHit<UserContentDocument>> candidates) {

        // Pass 1: raw 후보 + max 인기도 추출
        List<RawCandidate> rawList = candidates.stream()
                .map(this::toRawCandidate)
                .toList();

        double maxPopularity = rawList.stream()
                .mapToDouble(RawCandidate::popularityRaw)
                .max().orElse(1.0);
        if (maxPopularity == 0.0) maxPopularity = 1.0;

        final double maxPop = maxPopularity;

        // Pass 2: 최종 점수 계산 → 정렬 → RESULT_SIZE 슬라이스
        return rawList.stream()
                .sorted(Comparator.comparingDouble(
                        (RawCandidate c) -> computeFinalScore(c, maxPop)
                ).reversed())
                .limit(RESULT_SIZE)
                .map(c -> UserRecommendedContentResponse.from(c.doc()))
                .toList();
    }

    private double computeFinalScore(RawCandidate c, double maxPopularity) {
        double popularityScore = c.popularityRaw() / maxPopularity;
        return W_SIMILARITY * c.similarity()
             + W_POPULARITY * popularityScore
             + W_FRESHNESS  * c.freshnessScore();
    }

    private RawCandidate toRawCandidate(SearchHit<UserContentDocument> hit) {
        UserContentDocument doc = hit.getContent();
        long views     = doc.getTotalViewCount() != null ? doc.getTotalViewCount() : 0L;
        long bookmarks = doc.getBookmarkCount()  != null ? doc.getBookmarkCount()  : 0L;
        double popularityRaw = Math.log1p(views) + Math.log1p(bookmarks) * 2.0;

        return new RawCandidate(doc, hit.getScore(), popularityRaw, freshnessScore(doc));
    }

    private double freshnessScore(UserContentDocument doc) {
        if (doc.getCreatedAt() == null) return 0.0;
        long days = ChronoUnit.DAYS.between(doc.getCreatedAt(), LocalDateTime.now());
        if (days < 0) days = 0;
        return Math.exp(-days / FRESHNESS_DECAY_DAYS);
    }

    // =========================================================
    //  Fallback — 0-벡터 감지 시 인기+신선도 기반 추천
    // =========================================================

    private UserRecommendationResponse fallbackRecommend(Long userId, boolean extended) {
        long totalActive  = countActiveUserContents();
        int candidateSize = calcCandidateSize(totalActive);

        List<SearchHit<UserContentDocument>> candidates = fallbackSearch(candidateSize);
        if (candidates.isEmpty()) {
            log.info("[유저콘텐츠 추천-Fallback] userId={} 콘텐츠 없음 → 빈 결과 반환", userId);
            return new UserRecommendationResponse(List.of(), false);
        }

        List<UserRecommendedContentResponse> ranked = rankFallback(candidates);

        if (extended) {
            return new UserRecommendationResponse(ranked, false);
        } else {
            List<UserRecommendedContentResponse> initial =
                    ranked.subList(0, Math.min(INITIAL_SIZE, ranked.size()));
            boolean hasMore = ranked.size() > INITIAL_SIZE;
            log.info("[유저콘텐츠 추천-Fallback] userId={} initial → {}개 반환, hasMore={}", userId, initial.size(), hasMore);
            return new UserRecommendationResponse(initial, hasMore);
        }
    }

    private List<SearchHit<UserContentDocument>> fallbackSearch(int candidateSize) {
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.term(t -> t.field("contentStatus").value("ACTIVE")))
                .withSort(Sort.by(Sort.Direction.DESC, "totalViewCount"))
                .withPageable(Pageable.ofSize(candidateSize))
                .build();

        SearchHits<UserContentDocument> hits =
                elasticsearchOperations.search(query, UserContentDocument.class);
        log.info("[유저콘텐츠 추천-Fallback] ES 후보 {}개 조회", hits.getTotalHits());
        return hits.getSearchHits();
    }

    private List<UserRecommendedContentResponse> rankFallback(
            List<SearchHit<UserContentDocument>> candidates) {

        List<RawCandidate> rawList = candidates.stream()
                .map(this::toRawCandidate)
                .toList();

        double maxPopularity = rawList.stream()
                .mapToDouble(RawCandidate::popularityRaw)
                .max().orElse(1.0);
        if (maxPopularity == 0.0) maxPopularity = 1.0;

        final double maxPop = maxPopularity;
        return rawList.stream()
                .sorted(Comparator.comparingDouble((RawCandidate c) -> {
                    double popScore = c.popularityRaw() / maxPop;
                    return W_POPULARITY_FALLBACK * popScore
                         + W_FRESHNESS_FALLBACK  * c.freshnessScore();
                }).reversed())
                .limit(RESULT_SIZE)
                .map(c -> UserRecommendedContentResponse.from(c.doc()))
                .toList();
    }

    // =========================================================
    //  0-벡터 Fallback — 피드 인기순 노출
    // =========================================================

    /**
     * 0-벡터(선호 태그 없음 또는 seedId 문서 미존재) 시 인기순 피드 Fallback.
     * kNN 없이 ACTIVE 유저 콘텐츠를 조회수 내림차순으로 size개 반환한다.
     */
    private UserFeedResponse fallbackFeed(int size) {
        try {
            NativeQuery query = NativeQuery.builder()
                    .withQuery(q -> q.term(t -> t.field("contentStatus").value("ACTIVE")))
                    .withSort(Sort.by(Sort.Direction.DESC, "totalViewCount"))
                    .withPageable(Pageable.ofSize(size))
                    .build();

            SearchHits<UserContentDocument> hits =
                    elasticsearchOperations.search(query, UserContentDocument.class);

            List<UserRecommendedContentResponse> items = hits.getSearchHits().stream()
                    .map(h -> UserRecommendedContentResponse.from(h.getContent()))
                    .toList();

            Long nextSeedId = items.isEmpty() ? null : items.get(items.size() - 1).userContentId();
            log.info("[유저콘텐츠 피드-Fallback] 인기순 {}개 반환", items.size());
            return new UserFeedResponse(items, nextSeedId, items.size() == size);

        } catch (Exception e) {
            log.warn("[유저콘텐츠 피드-Fallback] 실패 → 빈 결과: {}", e.getMessage());
            return new UserFeedResponse(List.of(), null, false);
        }
    }

    // =========================================================
    //  ES DOWN Fallback — DB 인기순 추천
    // =========================================================

    private UserRecommendationResponse recommendFromDb(Long userId, boolean extended) {
        try {
            List<UserContent> contents = userContentRepository.findTopActiveByPopularity(
                    PageRequest.of(0, RESULT_SIZE)
            );

            int limit = extended ? RESULT_SIZE : INITIAL_SIZE;
            List<UserRecommendedContentResponse> items = contents.stream()
                    .limit(limit)
                    .map(this::toResponseFromUserContent)
                    .toList();

            boolean hasMore = !extended && contents.size() > INITIAL_SIZE;
            log.info("[유저콘텐츠 추천-DB Fallback] userId={} → {}개 반환", userId, items.size());
            return new UserRecommendationResponse(items, hasMore);

        } catch (Exception dbEx) {
            log.error("[유저콘텐츠 추천 ES+DB DOWN] DB Fallback도 실패 → 빈 결과: {}", dbEx.getMessage());
            return new UserRecommendationResponse(List.of(), false);
        }
    }

    private UserRecommendedContentResponse toResponseFromUserContent(UserContent uc) {
        List<String> tagNames = uc.getParentContent().getContentTags().stream()
                .map(ct -> ct.getTag().getName())
                .collect(Collectors.toList());
        return new UserRecommendedContentResponse(
                uc.getId(),
                uc.getParentContent().getId(),
                uc.getTitle(),
                uc.getParentContent().getThumbnailUrl(),
                uc.getAccessLevel().name(),
                uc.getTotalViewCount(),
                uc.getBookmarkCount(),
                tagNames
        );
    }

    // =========================================================
    //  보조 메서드
    // =========================================================

    private boolean isZeroVector(float[] vector) {
        double normSq = 0.0;
        for (float v : vector) normSq += (double) v * v;
        return normSq <= ZERO_VECTOR_EPS;
    }
}
