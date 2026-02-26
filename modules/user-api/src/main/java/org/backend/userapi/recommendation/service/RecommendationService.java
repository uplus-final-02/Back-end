package org.backend.userapi.recommendation.service;

import content.repository.WatchHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.backend.userapi.recommendation.dto.RecommendationResponse;
import org.backend.userapi.recommendation.dto.RecommendedContentResponse;
import org.backend.userapi.search.document.ContentDocument;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import user.repository.UserPreferredTagRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * HNSW 기반 하이브리드 개인화 추천 서비스.
 *
 * ┌─────────────────────────────────────────────────────────────────────┐
 * │  Stage 1 (외부 / 빠름): Elasticsearch kNN (HNSW)                    │
 * │    유저 선호 태그 → 100차원 쿼리 벡터 → kNN 코사인 유사도 검색        │
 * │    ACTIVE 콘텐츠 중 상위 CANDIDATE_SIZE(100)개 후보 추출             │
 * │                                                                     │
 * │  Stage 2 (내부 / 정밀): 랭킹                                         │
 * │    점수 = kNN 코사인 유사도 + 인기도 보너스 - 시청 패널티              │
 * │    최종 RESULT_SIZE(50)개 정렬                                       │
 * │                                                                     │
 * │  2-tier 응답                                                        │
 * │    기본:        상위 INITIAL_SIZE(15)개 반환, hasMore: true          │
 * │    extended:    상위 RESULT_SIZE(50)개 반환, hasMore: false          │
 * │                                                                     │
 * │  스케일 로드맵                                                        │
 * │    현재  (~1천개):  k=100  → 랭킹 → 50개                            │
 * │    중기   (~만개):  k=1000 → 랭킹 → 50개  (k값만 상향 조정)          │
 * └─────────────────────────────────────────────────────────────────────┘
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final UserPreferredTagRepository userPreferredTagRepository;
    private final WatchHistoryRepository watchHistoryRepository;
    private final TagVectorService tagVectorService;
    private final ElasticsearchOperations elasticsearchOperations;

    /** Stage 1: ES kNN 후보 수 (HNSW 탐색 풀) */
    private static final int CANDIDATE_SIZE = 100;

    /** Stage 2: 최종 랭킹 보관 수 (extended 시 전량 반환) */
    private static final int RESULT_SIZE = 50;

    /** 기본 노출 수 (3행 × 5열) */
    private static final int INITIAL_SIZE = 15;

    /** 시청 이력 패널티 적용 기간 (개월) */
    private static final int WATCH_MONTHS = 3;

    /**
     * 개인화 추천 콘텐츠 반환.
     *
     * @param userId   현재 유저 ID
     * @param extended false → 15개 기본 노출 / true → 50개 전체 노출
     */
    @Transactional(readOnly = true)
    public RecommendationResponse recommend(Long userId, boolean extended) {

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

        // 2. 유저 쿼리 벡터 생성 (100차원, 선호 태그 위치 = 1.0f, 나머지 = 0.0f)
        float[] queryVector = tagVectorService.buildUserVector(preferredTagIds);

        // 3. Stage 1: ES kNN 후보 CANDIDATE_SIZE(100)개 추출
        List<SearchHit<ContentDocument>> candidates = knnSearch(queryVector);

        if (candidates.isEmpty()) {
            log.info("[추천] userId={} kNN 결과 없음 → 빈 결과 반환", userId);
            return new RecommendationResponse(List.of(), false);
        }

        // 4. 최근 시청 콘텐츠 조회 (하단 배치용 패널티 대상)
        LocalDateTime since = LocalDateTime.now().minusMonths(WATCH_MONTHS);
        Set<Long> watchedIds = watchHistoryRepository
                .findRecentWatchedContentIds(userId, since)
                .stream()
                .collect(Collectors.toSet());

        // 5. Stage 2: 점수 기반 랭킹 → 상위 RESULT_SIZE(50)개 확보
        List<RecommendedContentResponse> top50 = rank(candidates, watchedIds);

        // 6. 2-tier 응답 결정
        if (extended) {
            // 더 알아보기: 상위 50개 전체 반환
            log.info("[추천] userId={} extended → {}개 반환", userId, top50.size());
            return new RecommendationResponse(top50, false);
        } else {
            // 기본 노출: 상위 15개, 나머지 있으면 hasMore=true
            List<RecommendedContentResponse> initial =
                    top50.subList(0, Math.min(INITIAL_SIZE, top50.size()));
            boolean hasMore = top50.size() > INITIAL_SIZE;
            log.info("[추천] userId={} initial → {}개 반환, hasMore={}", userId, initial.size(), hasMore);
            return new RecommendationResponse(initial, hasMore);
        }
    }

    // ── Private ─────────────────────────────────────────────────

    /**
     * ES kNN 검색.
     * numCandidates = CANDIDATE_SIZE * 2 → HNSW 탐색 품질 확보.
     */
    private List<SearchHit<ContentDocument>> knnSearch(float[] queryVector) {
        List<Float> queryVectorList = new ArrayList<>();
        for (float v : queryVector) {
            queryVectorList.add(v);
        }

        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.knn(knn -> knn
                        .field("tagVector")
                        .queryVector(queryVectorList)
                        .numCandidates(CANDIDATE_SIZE * 2)
                        .k(CANDIDATE_SIZE)
                        .filter(f -> f.term(t -> t
                                .field("status")
                                .value("ACTIVE")))
                ))
                .withPageable(Pageable.ofSize(CANDIDATE_SIZE))
                .build();

        SearchHits<ContentDocument> hits =
                elasticsearchOperations.search(query, ContentDocument.class);
        log.info("[추천] Stage 1 kNN 후보 {}개 추출", hits.getTotalHits());

        return hits.getSearchHits();
    }

    /**
     * Stage 2 랭킹: 점수 내림차순 → 상위 RESULT_SIZE(50)개 반환.
     *
     * <pre>
     *   점수 = kNN 코사인 유사도 (0~1)
     *        + log1p(조회수)  × 0.00003   인기도 보너스
     *        + log1p(북마크수) × 0.00005   큐레이션 가치 보너스
     *        - 10.0                        최근 시청 콘텐츠 패널티 (하단 배치)
     * </pre>
     */
    private List<RecommendedContentResponse> rank(
            List<SearchHit<ContentDocument>> candidates,
            Set<Long> watchedIds) {

        return candidates.stream()
                .sorted(Comparator.comparingDouble(
                        (SearchHit<ContentDocument> hit) -> computeScore(hit, watchedIds)
                ).reversed())
                .limit(RESULT_SIZE)
                .map(hit -> RecommendedContentResponse.from(hit.getContent()))
                .toList();
    }

    private double computeScore(SearchHit<ContentDocument> hit, Set<Long> watchedIds) {
        ContentDocument doc = hit.getContent();
        double score = hit.getScore();  // kNN 코사인 유사도 (0 ~ 1)

        long views     = doc.getTotalViewCount() != null ? doc.getTotalViewCount() : 0L;
        long bookmarks = doc.getBookmarkCount()  != null ? doc.getBookmarkCount()  : 0L;
        score += Math.log1p(views)     * 0.00003;
        score += Math.log1p(bookmarks) * 0.00005;

        // 이미 시청한 콘텐츠는 점수 대폭 감점 → 자연스럽게 하단 배치
        if (watchedIds.contains(doc.getContentId())) {
            score -= 10.0;
        }

        return score;
    }
}
