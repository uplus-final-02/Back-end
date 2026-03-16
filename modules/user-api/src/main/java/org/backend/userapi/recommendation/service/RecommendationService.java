package org.backend.userapi.recommendation.service;

import common.enums.HistoryStatus;
import content.entity.Content;
import content.repository.ContentRepository;
import content.repository.WatchHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.backend.userapi.recommendation.dto.RecommendationResponse;
import org.backend.userapi.recommendation.dto.RecommendedContentResponse;
import org.backend.userapi.search.document.ContentDocument;
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
 * HNSW 기반 2-Stage 하이브리드 개인화 추천 서비스.
 *
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │  Stage 1  (ES / HNSW kNN)  — 빠른 후보 추출                             │
 * │    유저 선호 태그 → 100차원 쿼리 벡터 → 코사인 유사도 kNN                  │
 * │    ACTIVE 콘텐츠 중 전체의 1/3 후보 추출 (동적 계산)                       │
 * │    e.g. 콘텐츠 100개 → 33 후보 / 10,000개 → 최대 1,000 후보              │
 * │                                                                         │
 * │  Stage 2  (내부 / 정밀 랭킹)  — 최종 50개 선정                            │
 * │                                                                         │
 * │  최종 점수 = W_SIMILARITY  × tagSimilarity   (ES kNN 코사인, 0~1)       │
 * │           + W_POPULARITY  × popularityScore  (후보 내 정규화, 0~1)      │
 * │           + W_FRESHNESS   × freshnessScore   (지수 감쇠, 0~1)          │
 * │           + watchPenalty                     (시청 이력 차등 감점)       │
 * │                                                                         │
 * │  watchPenalty                                                            │
 * │    STARTED   (0~60초)   → -0.70  거의 안 봄, 강하게 하단 배치             │
 * │    WATCHING  (60초~90%) → -0.30  시청 중, 중간 배치                      │
 * │    COMPLETED (90% 이상) → -0.15  완료, 살짝만 내림 (재시청 고려)           │
 * │                                                                         │
 * │  2-tier 응답                                                             │
 * │    기본 (hasMore: true) : 상위 15개    ← 홈 화면 초기 노출                │
 * │    extended             : 상위 50개    ← "더 알아보기" 클릭 시             │
 * │                                                                         │
 * └─────────────────────────────────────────────────────────────────────────┘
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final UserPreferredTagRepository userPreferredTagRepository;
    private final WatchHistoryRepository watchHistoryRepository;
    private final TagVectorService tagVectorService;
    private final ElasticsearchOperations elasticsearchOperations;
    private final ContentRepository contentRepository;

    // ── Stage 1 후보 크기 ─────────────────────────────────────────
    /** 후보 최소값: 랭킹 품질 보장을 위한 하한선 */
    private static final int MIN_CANDIDATE_SIZE = 30;
    /** 후보 최대값: 만개 이상 스케일에서도 Stage 2 성능 보장 */
    private static final int MAX_CANDIDATE_SIZE = 1_000;

    // ── Stage 2 결과 크기 ─────────────────────────────────────────
    /** 최종 보관 수 (extended 시 전량 반환) */
    private static final int RESULT_SIZE  = 50;
    /** 기본 노출 수 (홈 3행 × 5열) */
    private static final int INITIAL_SIZE = 15;

    // ── 시청 이력 기간 ────────────────────────────────────────────
    private static final int WATCH_MONTHS = 3;

    // ── 점수 가중치 (합계 = 1.0) ──────────────────────────────────
    /** 태그 유사도 — 유저 취향 매칭 (핵심 신호) */
    private static final double W_SIMILARITY  = 0.60;
    /** 인기도 — 조회수 + 북마크 기반 후보 내 상대 정규화 */
    private static final double W_POPULARITY  = 0.25;
    /** 신선도 — 최신 콘텐츠 우대 (지수 감쇠) */
    private static final double W_FRESHNESS   = 0.15;

    // ── 시청 이력 패널티 ──────────────────────────────────────────
    /** 거의 안 본 콘텐츠 (0~60초) — 강하게 하단 배치 */
    private static final double PENALTY_STARTED   = -0.70;
    /** 시청 중인 콘텐츠 (60초~90%) — 중간 배치 */
    private static final double PENALTY_WATCHING  = -0.30;
    /** 완료 콘텐츠 (90% 이상) — 살짝만 내림, 재시청 고려 */
    private static final double PENALTY_COMPLETED = -0.15;

    // ── 신선도 감쇠 반감기 ────────────────────────────────────────
    /** 이 일수가 지나면 freshnessScore 가 약 0.37로 감소 (e^-1 지점) */
    private static final double FRESHNESS_DECAY_DAYS = 365.0;

    // ── 0-벡터 방어 ──────────────────────────────────────────────
    /**
     * 유저 쿼리 벡터의 L2 norm² 이 이 값 이하면 0-벡터로 판단.
     * 부동소수점 오차 허용치.
     */
    private static final float ZERO_VECTOR_EPS = 1e-6f;

    // ── Fallback 가중치 (0-벡터 시 유사도 항목 없으므로 재배분) ──
    /** Fallback 인기도 가중치 (정상 W_SIMILARITY 0.60 흡수) */
    private static final double W_POPULARITY_FALLBACK = 0.60;
    /** Fallback 신선도 가중치 */
    private static final double W_FRESHNESS_FALLBACK  = 0.40;

    // ── 내부 집계용 임시 레코드 ───────────────────────────────────
    private record RawCandidate(
            ContentDocument doc,
            double similarity,     // ES kNN 코사인 (0~1)
            double popularityRaw,  // 정규화 전 인기도 (log 변환값)
            double freshnessScore  // 이미 0~1 정규화 완료
    ) {}

    // =========================================================
    //  Public API
    // =========================================================

    /**
     * 개인화 추천 콘텐츠 반환.
     *
     * @param userId   현재 로그인 유저 ID
     * @param extended false → 15개 기본 / true → 50개 "더 알아보기"
     */
    @Transactional(readOnly = true)
    public RecommendationResponse recommend(Long userId, boolean extended) {
        try {
            // 1. 유저 선호 태그 조회
            List<Long> preferredTagIds = userPreferredTagRepository
                    .findAllByUserIdWithTag(userId)
                    .stream()
                    .map(upt -> upt.getTag().getId())
                    .toList();

            if (preferredTagIds.isEmpty()) {
                log.info("[추천] userId={} 선호 태그 없음 → 빈 결과 반환", userId);
                return new RecommendationResponse(List.of(), false);
            }

            // 2. 100차원 유저 쿼리 벡터 (선호 태그 = 1.0f, 나머지 = 0.0f)
            float[] queryVector = tagVectorService.buildUserVector(preferredTagIds);

            // 2-1. 0-벡터 방어: 선호 태그가 TagVectorService 인덱스에 하나도 매핑 안 된 경우
            //      (태그가 비활성화되거나 아직 인덱스에 없을 때 발생)
            //      kNN에 0-벡터를 보내면 코사인 유사도 계산 불가 → Fallback(인기+신선도) 사용
            if (isZeroVector(queryVector)) {
                log.info("[추천] userId={} 0-벡터 감지 (선호 태그가 벡터 인덱스 미매핑) → Fallback 모드", userId);
                return fallbackRecommend(userId, extended);
            }

            // 3. 동적 후보 크기 계산 (전체 콘텐츠의 1/3)
            long totalActive  = countActiveContents();
            int candidateSize = calcCandidateSize(totalActive);
            log.info("[추천] userId={} 총 활성 콘텐츠={} → Stage1 후보 목표={}", userId, totalActive, candidateSize);

            // 4. Stage 1: ES kNN — 코사인 유사도 기반 후보 추출
            List<SearchHit<ContentDocument>> candidates = knnSearch(queryVector, candidateSize);
            if (candidates.isEmpty()) {
                log.info("[추천] userId={} kNN 결과 없음 → 빈 결과 반환", userId);
                return new RecommendationResponse(List.of(), false);
            }

            // 5. 시청 이력 조회 (contentId → 최고 HistoryStatus)
            LocalDateTime since          = LocalDateTime.now().minusMonths(WATCH_MONTHS);
            Map<Long, HistoryStatus> watchStatusMap = buildWatchStatusMap(userId, since);

            // 6. Stage 2: 2-pass 내부 랭킹 → 상위 RESULT_SIZE(50)개
            List<RecommendedContentResponse> ranked = rank(candidates, watchStatusMap);

            // 7. 2-tier 응답 결정
            if (extended) {
                log.info("[추천] userId={} extended → {}개 반환", userId, ranked.size());
                return new RecommendationResponse(ranked, false);
            } else {
                List<RecommendedContentResponse> initial =
                        ranked.subList(0, Math.min(INITIAL_SIZE, ranked.size()));
                boolean hasMore = ranked.size() > INITIAL_SIZE;
                log.info("[추천] userId={} initial → {}개 반환, hasMore={}", userId, initial.size(), hasMore);
                return new RecommendationResponse(initial, hasMore);
            }

        } catch (DataAccessException e) {
            log.warn("[ES DOWN] 추천 ES 연결 실패 → DB 인기순 Fallback: userId={}", userId);
            return recommendFromDb(userId, extended);
        }
    }

    // =========================================================
    //  Fallback — 0-벡터 감지 시 인기+신선도 기반 추천
    // =========================================================

    /**
     * 0-벡터 Fallback 추천.
     * 유저 쿼리 벡터를 사용할 수 없으므로 kNN 없이 ES에서 ACTIVE 콘텐츠를
     * 조회수 기준으로 candidateSize 개 가져온 뒤, 인기+신선도로 내부 랭킹.
     *
     * <pre>
     *   fallbackScore = W_POPULARITY_FALLBACK × popularityScore  (0.60)
     *                 + W_FRESHNESS_FALLBACK   × freshnessScore   (0.40)
     *                 + watchPenalty
     * </pre>
     */
    private RecommendationResponse fallbackRecommend(Long userId, boolean extended) {
        long totalActive  = countActiveContents();
        int candidateSize = calcCandidateSize(totalActive);

        List<SearchHit<ContentDocument>> candidates = fallbackSearch(candidateSize);
        if (candidates.isEmpty()) {
            log.info("[추천-Fallback] userId={} 콘텐츠 없음 → 빈 결과 반환", userId);
            return new RecommendationResponse(List.of(), false);
        }

        LocalDateTime since = LocalDateTime.now().minusMonths(WATCH_MONTHS);
        Map<Long, HistoryStatus> watchStatusMap = buildWatchStatusMap(userId, since);

        List<RecommendedContentResponse> ranked = rankFallback(candidates, watchStatusMap);

        if (extended) {
            log.info("[추천-Fallback] userId={} extended → {}개 반환", userId, ranked.size());
            return new RecommendationResponse(ranked, false);
        } else {
            List<RecommendedContentResponse> initial =
                    ranked.subList(0, Math.min(INITIAL_SIZE, ranked.size()));
            boolean hasMore = ranked.size() > INITIAL_SIZE;
            log.info("[추천-Fallback] userId={} initial → {}개 반환, hasMore={}", userId, initial.size(), hasMore);
            return new RecommendationResponse(initial, hasMore);
        }
    }

    /**
     * Fallback용 ES 쿼리 (kNN 없이 조회수 내림차순).
     * ES 단에서 1차 정렬 후 candidateSize 개를 가져와 내부 랭킹 부담 경감.
     */
    private List<SearchHit<ContentDocument>> fallbackSearch(int candidateSize) {
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.term(t -> t.field("status").value("ACTIVE")))
                .withSort(Sort.by(Sort.Direction.DESC, "totalViewCount"))
                .withPageable(Pageable.ofSize(candidateSize))
                .build();

        SearchHits<ContentDocument> hits =
                elasticsearchOperations.search(query, ContentDocument.class);
        log.info("[추천-Fallback] ES 후보 {}개 조회 (요청 {}개)", hits.getTotalHits(), candidateSize);

        return hits.getSearchHits();
    }

    /**
     * Fallback 랭킹 (유사도 항목 없이 인기+신선도 가중치 재배분).
     * 정상 rank() 와 동일한 2-pass 구조 유지.
     */
    private List<RecommendedContentResponse> rankFallback(
            List<SearchHit<ContentDocument>> candidates,
            Map<Long, HistoryStatus> watchStatusMap) {

        List<RawCandidate> rawList = candidates.stream()
                .map(hit -> toRawCandidate(hit))   // similarity 값은 무시
                .toList();

        double maxPopularity = rawList.stream()
                .mapToDouble(RawCandidate::popularityRaw)
                .max().orElse(1.0);
        if (maxPopularity == 0.0) maxPopularity = 1.0;

        final double maxPop = maxPopularity;
        return rawList.stream()
                .sorted(Comparator.comparingDouble((RawCandidate c) -> {
                    double popularityScore = c.popularityRaw() / maxPop;
                    double score = W_POPULARITY_FALLBACK * popularityScore
                                 + W_FRESHNESS_FALLBACK  * c.freshnessScore();

                    HistoryStatus watchStatus = watchStatusMap.get(c.doc().getContentId());
                    if (watchStatus != null) {
                        score += switch (watchStatus) {
                            case STARTED   -> PENALTY_STARTED;
                            case WATCHING  -> PENALTY_WATCHING;
                            case COMPLETED -> PENALTY_COMPLETED;
                        };
                    }
                    return score;
                }).reversed())
                .limit(RESULT_SIZE)
                .map(c -> RecommendedContentResponse.from(c.doc()))
                .toList();
    }

    // =========================================================
    //  Stage 1 — ES kNN
    // =========================================================

    /**
     * ES ACTIVE 콘텐츠 수 조회.
     * Stage 1 후보 크기를 동적으로 계산하기 위해 사용.
     */
    private long countActiveContents() {
        NativeQuery countQuery = NativeQuery.builder()
                .withQuery(q -> q.term(t -> t.field("status").value("ACTIVE")))
                .withPageable(Pageable.ofSize(1))
                .build();
        return elasticsearchOperations.count(countQuery, ContentDocument.class);
    }

    /**
     * Stage 1 후보 크기 계산.
     *
     * <pre>
     *   candidateSize = totalActive / 3   (1/3 룰)
     *   최소: MIN_CANDIDATE_SIZE(30)   — 랭킹 품질 보장
     *   최대: MAX_CANDIDATE_SIZE(1000) — Stage 2 성능 보장
     *
     *   예시
     *   │  콘텐츠 수  │ candidateSize │
     *   │    100     │      33       │
     *   │  1,000     │     333       │
     *   │ 10,000     │   1,000(캡)   │
     * </pre>
     */
    private int calcCandidateSize(long totalActive) {
        if (totalActive == 0) return MIN_CANDIDATE_SIZE;
        int size = (int) (totalActive / 3);
        return Math.min(Math.max(size, MIN_CANDIDATE_SIZE), MAX_CANDIDATE_SIZE);
    }

    /**
     * ES kNN 검색.
     * numCandidates = k × 2 로 HNSW 탐색 품질 확보 (더 넓게 탐색 후 k개 선택).
     */
    private List<SearchHit<ContentDocument>> knnSearch(float[] queryVector, int k) {
        List<Float> queryVectorList = new ArrayList<>();
        for (float v : queryVector) {
            queryVectorList.add(v);
        }

        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.knn(knn -> knn
                        .field("tagVector")
                        .queryVector(queryVectorList)
                        .numCandidates(k * 2)
                        .k(k)
                        .filter(f -> f.term(t -> t
                                .field("status")
                                .value("ACTIVE")))
                ))
                .withPageable(Pageable.ofSize(k))
                .build();

        SearchHits<ContentDocument> hits =
                elasticsearchOperations.search(query, ContentDocument.class);
        log.info("[추천] Stage 1 완료 — kNN 후보 {}개 추출 (요청 k={})", hits.getTotalHits(), k);

        return hits.getSearchHits();
    }

    // =========================================================
    //  Stage 2 — 내부 랭킹
    // =========================================================

    /**
     * 2-pass 랭킹.
     *
     * <pre>
     *   Pass 1: 모든 후보의 popularityRaw 계산 → max 값 추출 (정규화 기준)
     *   Pass 2: 최종 점수 계산 → 내림차순 정렬 → 상위 RESULT_SIZE 반환
     * </pre>
     */
    private List<RecommendedContentResponse> rank(
            List<SearchHit<ContentDocument>> candidates,
            Map<Long, HistoryStatus> watchStatusMap) {

        // Pass 1: raw 후보 목록 생성 + 인기도 max 추출
        List<RawCandidate> rawList = candidates.stream()
                .map(this::toRawCandidate)
                .toList();

        double maxPopularity = rawList.stream()
                .mapToDouble(RawCandidate::popularityRaw)
                .max()
                .orElse(1.0);
        if (maxPopularity == 0.0) maxPopularity = 1.0;  // 0 나눗셈 방지

        // Pass 2: 최종 점수 계산 → 정렬 → 상위 RESULT_SIZE 슬라이스
        final double maxPop = maxPopularity;

        // 디버그 로그: 각 후보의 점수 구성 출력 (운영 배포 전 log.debug 로 변경)
        rawList.forEach(c -> {
            double popScore     = c.popularityRaw() / maxPop;
            HistoryStatus ws    = watchStatusMap.get(c.doc().getContentId());
            double watchPenalty = ws == null ? 0.0 : switch (ws) {
                case STARTED   -> PENALTY_STARTED;
                case WATCHING  -> PENALTY_WATCHING;
                case COMPLETED -> PENALTY_COMPLETED;
            };
            double total = W_SIMILARITY * c.similarity()
                         + W_POPULARITY * popScore
                         + W_FRESHNESS  * c.freshnessScore()
                         + watchPenalty;
            log.info("[점수] contentId={} title='{}' | 유사도={}×{} 인기={}×{} 신선도={}×{} 패널티={} → 합계={}",
                    c.doc().getContentId(),
                    c.doc().getTitle(),
                    String.format("%.2f", W_SIMILARITY),  String.format("%.3f", c.similarity()),
                    String.format("%.2f", W_POPULARITY),  String.format("%.3f", popScore),
                    String.format("%.2f", W_FRESHNESS),   String.format("%.3f", c.freshnessScore()),
                    String.format("%.2f", watchPenalty),
                    String.format("%.4f", total));
        });

        return rawList.stream()
                .sorted(Comparator.comparingDouble(
                        (RawCandidate c) -> computeFinalScore(c, maxPop, watchStatusMap)
                ).reversed())
                .limit(RESULT_SIZE)
                .map(c -> RecommendedContentResponse.from(c.doc()))
                .toList();
    }

    /**
     * SearchHit → RawCandidate 변환.
     * popularityRaw 는 후보 집합 내 상대 정규화 전 log 변환 값.
     */
    private RawCandidate toRawCandidate(SearchHit<ContentDocument> hit) {
        ContentDocument doc = hit.getContent();

        // 인기도 raw (log 변환: 조회수 + 북마크 2배 가중)
        long views     = doc.getTotalViewCount() != null ? doc.getTotalViewCount() : 0L;
        long bookmarks = doc.getBookmarkCount()  != null ? doc.getBookmarkCount()  : 0L;
        double popularityRaw = Math.log1p(views) + Math.log1p(bookmarks) * 2.0;

        return new RawCandidate(
                doc,
                hit.getScore(),            // ES kNN 코사인 (0~1)
                popularityRaw,
                freshnessScore(doc)        // 지수 감쇠 (0~1)
        );
    }

    /**
     * 최종 점수 계산.
     *
     * <pre>
     *   finalScore = W_SIMILARITY     × similarity
     *              + W_POPULARITY     × (popularityRaw / maxPopularity)
     *              + W_FRESHNESS      × freshnessScore
     *              + W_POPULARITY_RANK × popRankScore    ← TODO
     *              + watchPenalty
     * </pre>
     */
    private double computeFinalScore(RawCandidate c, double maxPopularity,
                                     Map<Long, HistoryStatus> watchStatusMap) {

        double popularityScore = c.popularityRaw() / maxPopularity;  // 0~1 정규화

        double score = W_SIMILARITY  * c.similarity()
                     + W_POPULARITY  * popularityScore
                     + W_FRESHNESS   * c.freshnessScore();

        // 시청 이력 패널티 적용
        HistoryStatus watchStatus = watchStatusMap.get(c.doc().getContentId());
        if (watchStatus != null) {
            score += switch (watchStatus) {
                case STARTED   -> PENALTY_STARTED;    // -0.70: 거의 안 봄
                case WATCHING  -> PENALTY_WATCHING;   // -0.30: 시청 중
                case COMPLETED -> PENALTY_COMPLETED;  // -0.15: 완료 (재시청 가능)
            };
        }

        return score;
    }

    // =========================================================
    //  보조 메서드
    // =========================================================

    /**
     * 신선도 점수 (지수 감쇠).
     *
     * <pre>
     *   freshnessScore = exp(-경과일 / FRESHNESS_DECAY_DAYS)
     *
     *   경과일   점수
     *     0     1.00  (오늘 올라온 신규 콘텐츠)
     *   180     0.61
     *   365     0.37  (1년 된 콘텐츠)
     *   730     0.14  (2년 된 콘텐츠)
     * </pre>
     */
    private double freshnessScore(ContentDocument doc) {
        if (doc.getCreatedAt() == null) return 0.0;
        long days = ChronoUnit.DAYS.between(doc.getCreatedAt(), LocalDateTime.now());
        if (days < 0) days = 0;  // 미래 날짜 방어
        return Math.exp(-days / FRESHNESS_DECAY_DAYS);
    }

    /**
     * 시청 이력 상태 맵 구성.
     * 동일 콘텐츠를 여러 에피소드로 시청한 경우 가장 높은 상태를 선택.
     * COMPLETED > WATCHING > STARTED
     *
     * @return Map&lt;contentId, 최고 HistoryStatus&gt;
     */
    private Map<Long, HistoryStatus> buildWatchStatusMap(Long userId, LocalDateTime since) {
        List<Object[]> rows = watchHistoryRepository
                .findRecentWatchedContentWithStatus(userId, since);

        Map<Long, HistoryStatus> map = new HashMap<>();
        for (Object[] row : rows) {
            Long contentId      = (Long) row[0];
            HistoryStatus status = (HistoryStatus) row[1];
            map.merge(contentId, status, (existing, incoming) ->
                    statusPriority(incoming) > statusPriority(existing) ? incoming : existing
            );
        }
        return map;
    }

    /** COMPLETED(3) > WATCHING(2) > STARTED(1) */
    private int statusPriority(HistoryStatus status) {
        return switch (status) {
            case COMPLETED -> 3;
            case WATCHING  -> 2;
            case STARTED   -> 1;
        };
    }

    // =========================================================
    //  ES DOWN Fallback — DB 인기순 추천
    // =========================================================

    /**
     * ES 다운 시 DB 인기순 Fallback 추천.
     * kNN 없이 조회수+북마크 기준 상위 콘텐츠를 반환.
     * 시청 완료 콘텐츠(COMPLETED)는 목록에서 제외.
     */
    private RecommendationResponse recommendFromDb(Long userId, boolean extended) {
        try {
            List<Content> contents = contentRepository.findTopActiveByPopularity(
                    PageRequest.of(0, RESULT_SIZE)
            );

            // 시청 이력 조회 (DB 직접 — ES 무관)
            LocalDateTime since = LocalDateTime.now().minusMonths(WATCH_MONTHS);
            Map<Long, HistoryStatus> watchStatusMap = buildWatchStatusMap(userId, since);

            int limit = extended ? RESULT_SIZE : INITIAL_SIZE;
            List<RecommendedContentResponse> items = contents.stream()
                    .filter(c -> watchStatusMap.get(c.getId()) != HistoryStatus.COMPLETED)
                    .limit(limit)
                    .map(this::toResponseFromContent)
                    .toList();

            boolean hasMore = !extended && contents.size() > INITIAL_SIZE;
            log.info("[추천-DB Fallback] userId={} → {}개 반환", userId, items.size());
            return new RecommendationResponse(items, hasMore);

        } catch (Exception dbEx) {
            log.error("[추천 ES+DB DOWN] DB Fallback도 실패 → 빈 결과 반환: {}", dbEx.getMessage());
            return new RecommendationResponse(List.of(), false);
        }
    }

    /**
     * Content 엔티티 → RecommendedContentResponse 직접 변환 (DB Fallback 전용).
     */
    private RecommendedContentResponse toResponseFromContent(Content content) {
        List<String> tagNames = content.getContentTags().stream()
                .map(ct -> ct.getTag().getName())
                .toList();
        return new RecommendedContentResponse(
                content.getId(),
                content.getTitle(),
                content.getType().name(),
                content.getThumbnailUrl(),
                content.getAccessLevel().name(),
                content.getTotalViewCount(),
                content.getBookmarkCount(),
                tagNames
        );
    }

    /**
     * 유저 쿼리 벡터가 0-벡터인지 판별.
     *
     * <pre>
     *   발생 케이스:
     *   ① 유저 선호 태그가 모두 isActive=false 로 비활성화된 경우
     *   ② 유저 선호 태그 ID가 TagVectorService 인덱스(tagIdToIndex)에 없는 경우
     *      (태그 추가 후 서버 재시작 전 상태 등)
     *
     *   판별 방식: L2 norm² ≤ ZERO_VECTOR_EPS
     * </pre>
     */
    private boolean isZeroVector(float[] vector) {
        double normSq = 0.0;
        for (float v : vector) {
            normSq += (double) v * v;
        }
        return normSq <= ZERO_VECTOR_EPS;
    }

}
