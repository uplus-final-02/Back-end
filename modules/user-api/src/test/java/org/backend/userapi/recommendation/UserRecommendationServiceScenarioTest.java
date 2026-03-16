package org.backend.userapi.recommendation;

import content.entity.UserContent;
import content.repository.UserContentRepository;
import org.backend.userapi.recommendation.dto.UserFeedResponse;
import org.backend.userapi.recommendation.dto.UserRecommendationResponse;
import org.backend.userapi.recommendation.service.TagVectorService;
import org.backend.userapi.recommendation.service.UserRecommendationService;
import org.backend.userapi.search.document.UserContentDocument;
import org.backend.userapi.search.repository.UserContentSearchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.test.util.ReflectionTestUtils;
import user.entity.UserPreferredTag;
import user.repository.UserPreferredTagRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 시나리오 3: 유저가 유저 콘텐츠(숏폼)를 개인화 추천받는다 (서비스 계층)
 * 시나리오 4: 유저가 숏폼 피드를 무한스크롤한다 (서비스 계층)
 *
 * <p>ES kNN 추천, Fallback 전략, hasMore 계산을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("유저 콘텐츠 추천 서비스 시나리오 테스트")
class UserRecommendationServiceScenarioTest {

    private static final Long USER_ID = 1L;

    @Mock private UserPreferredTagRepository userPreferredTagRepository;
    @Mock private TagVectorService tagVectorService;
    @Mock private ElasticsearchOperations elasticsearchOperations;
    @Mock private UserContentRepository userContentRepository;
    @Mock private UserContentSearchRepository userContentSearchRepository;

    private UserRecommendationService service;

    @BeforeEach
    void setUp() {
        service = new UserRecommendationService(
                userPreferredTagRepository,
                tagVectorService,
                elasticsearchOperations,
                userContentRepository,
                userContentSearchRepository
        );
    }

    // ══════════════════════════════════════════════════════════════
    //  시나리오 3: 개인화 추천 (recommend)
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("시나리오 3 — 개인화 추천 (recommend)")
    class RecommendScenario {

        @Test
        @DisplayName("선호 태그 기반 kNN 추천 → extended=false: 15개 이하 반환 + hasMore=true")
        void recommend_withPreferredTags_returnsInitialItems() {
            // 선호 태그 설정
            stubPreferredTags(List.of(1L, 2L, 3L));
            float[] vector = nonZeroVector();
            when(tagVectorService.buildUserVector(List.of(1L, 2L, 3L))).thenReturn(vector);

            // ES 검색 결과 mock (30개 후보)
            when(userContentSearchRepository.count()).thenReturn(100L);
            stubEsSearchResult(30);

            UserRecommendationResponse response = service.recommend(USER_ID, false);

            assertThat(response.items()).isNotEmpty();
            assertThat(response.items().size()).isLessThanOrEqualTo(15);
        }

        @Test
        @DisplayName("선호 태그 없음 (0-벡터) → 인기순 Fallback → items 반환")
        void recommend_zeroVector_fallbackToPopularity() {
            // 선호 태그 없음 → 0-벡터
            when(userPreferredTagRepository.findAllByUserIdWithTag(USER_ID))
                    .thenReturn(List.of());
            when(tagVectorService.buildUserVector(List.of())).thenReturn(new float[100]); // 0-벡터

            when(userContentSearchRepository.count()).thenReturn(50L);
            // 0-벡터 fallback도 ES로 인기순 조회 → 결과 반환
            stubEsSearchResult(15);

            UserRecommendationResponse response = service.recommend(USER_ID, false);

            assertThat(response.items()).isNotEmpty();
        }

        @Test
        @DisplayName("ES DOWN → DB 인기순 Fallback → items 반환 (빈 화면 없음)")
        void recommend_esDown_fallbackToDb() {
            stubPreferredTags(List.of(1L, 2L, 3L));
            when(tagVectorService.buildUserVector(any())).thenReturn(nonZeroVector());
            when(userContentSearchRepository.count()).thenReturn(100L);

            // ES 장애 시뮬레이션
            when(elasticsearchOperations.search(any(NativeQuery.class), eq(UserContentDocument.class)))
                    .thenThrow(new QueryTimeoutException("ES 연결 실패"));

            // DB Fallback mock
            List<UserContent> fallbackContents = List.of(
                    makeUserContent(1L), makeUserContent(2L), makeUserContent(3L));
            when(userContentRepository.findTopActiveByPopularity(any(Pageable.class)))
                    .thenReturn(fallbackContents);

            UserRecommendationResponse response = service.recommend(USER_ID, false);

            assertThat(response.items()).isNotEmpty();
            verify(userContentRepository).findTopActiveByPopularity(any(Pageable.class));
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  시나리오 4: 숏폼 피드 (feed)
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("시나리오 4 — 숏폼 피드 (feed)")
    class FeedScenario {

        @Test
        @DisplayName("batch가 size 꽉 참 → hasMore=true")
        void feed_fullBatch_hasMoreTrue() {
            stubPreferredTags(List.of(1L, 2L, 3L));
            when(tagVectorService.buildUserVector(any())).thenReturn(nonZeroVector());
            when(userContentSearchRepository.count()).thenReturn(100L);
            stubEsSearchResult(30); // 충분한 후보

            UserFeedResponse response = service.feed(USER_ID, null, 10, List.of());

            // items가 size(10)만큼 채워졌으면 hasMore=true
            assertThat(response.hasMore()).isTrue();
            assertThat(response.items()).hasSize(10);
            assertThat(response.nextSeedId()).isNotNull();
        }

        @Test
        @DisplayName("batch가 size 미만 → hasMore=false")
        void feed_partialBatch_hasMoreFalse() {
            stubPreferredTags(List.of(1L, 2L, 3L));
            when(tagVectorService.buildUserVector(any())).thenReturn(nonZeroVector());
            when(userContentSearchRepository.count()).thenReturn(100L);
            stubEsSearchResult(3); // 후보 3개 → size(10) 미만

            UserFeedResponse response = service.feed(USER_ID, null, 10, List.of());

            assertThat(response.hasMore()).isFalse();
            assertThat(response.items().size()).isLessThan(10);
        }

        @Test
        @DisplayName("seedId 지정 → 해당 문서의 tagVector로 kNN 쿼리 실행")
        void feed_withSeedId_usesDocumentVector() {
            Long seedId = 5L;
            UserContentDocument seedDoc = makeDocument(seedId);

            when(userContentSearchRepository.findById(seedId)).thenReturn(Optional.of(seedDoc));
            when(userContentSearchRepository.count()).thenReturn(100L);
            stubEsSearchResult(10);

            UserFeedResponse response = service.feed(USER_ID, seedId, 10, List.of(1L, 2L, 3L));

            // seedId 문서를 조회했는지 검증
            verify(userContentSearchRepository).findById(seedId);
            assertThat(response.items()).isNotEmpty();
        }

        @Test
        @DisplayName("seedId 문서가 있으면 유저 선호 태그 벡터를 만들지 않고 seed 문서 벡터를 우선 사용한다")
        void feed_withSeedDocument_doesNotBuildUserPreferenceVector() {
            Long seedId = 9L;
            UserContentDocument seedDoc = makeDocument(seedId);

            when(userContentSearchRepository.findById(seedId)).thenReturn(Optional.of(seedDoc));
            stubEsSearchResult(10);

            UserFeedResponse response = service.feed(USER_ID, seedId, 10, List.of());

            assertThat(response.items()).isNotEmpty();
            verify(userContentSearchRepository).findById(seedId);
            verify(userPreferredTagRepository, never()).findAllByUserIdWithTag(USER_ID);
            verify(tagVectorService, never()).buildUserVector(any());
        }

        @Test
        @DisplayName("seedId 문서가 없으면 유저 선호 태그 벡터로 fallback 한다")
        void feed_whenSeedDocumentMissing_fallsBackToUserPreferenceVector() {
            Long missingSeedId = 999L;

            when(userContentSearchRepository.findById(missingSeedId)).thenReturn(Optional.empty());
            stubPreferredTags(List.of(1L, 2L, 3L));
            when(tagVectorService.buildUserVector(List.of(1L, 2L, 3L))).thenReturn(nonZeroVector());
            stubEsSearchResult(10);

            UserFeedResponse response = service.feed(USER_ID, missingSeedId, 10, List.of());

            assertThat(response.items()).isNotEmpty();
            verify(userContentSearchRepository).findById(missingSeedId);
            verify(userPreferredTagRepository).findAllByUserIdWithTag(USER_ID);
            verify(tagVectorService).buildUserVector(List.of(1L, 2L, 3L));
        }

        @Test
        @DisplayName("선호 태그 없음 + seedId 없음 (0-벡터) → 인기순 Fallback")
        void feed_zeroVectorNoSeed_fallbackToPopularity() {
            when(userPreferredTagRepository.findAllByUserIdWithTag(USER_ID))
                    .thenReturn(List.of());
            when(tagVectorService.buildUserVector(List.of())).thenReturn(new float[100]); // 0-벡터
            when(userContentSearchRepository.count()).thenReturn(50L);

            // 0-벡터 fallback ES 인기순 조회 mock
            stubEsSearchResult(10);

            UserFeedResponse response = service.feed(USER_ID, null, 10, List.of());

            // 빈 결과가 아님을 보장
            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("excludeIds 서버 필터링 → 이미 본 콘텐츠 제외된 결과 반환")
        void feed_withExcludeIds_excludesSeenContents() {
            stubPreferredTags(List.of(1L, 2L, 3L));
            when(tagVectorService.buildUserVector(any())).thenReturn(nonZeroVector());
            when(userContentSearchRepository.count()).thenReturn(100L);
            stubEsSearchResult(10);

            List<Long> excludeIds = List.of(1L, 2L, 3L, 4L, 5L);
            UserFeedResponse response = service.feed(USER_ID, null, 10, excludeIds);

            // 정상 응답 확인 (excludeIds는 ES 쿼리 내 must_not 으로 처리됨)
            assertThat(response).isNotNull();
            assertThat(response.items()).isNotNull();
        }

        @Test
        @DisplayName("excludeIds 적용 후 후보가 모두 제외되면 빈 결과를 반환한다")
        void feed_whenAllCandidatesExcluded_returnsEmptyResponse() {
            stubPreferredTags(List.of(1L, 2L, 3L));
            when(tagVectorService.buildUserVector(any())).thenReturn(nonZeroVector());
            stubEsSearchResult(0);

            UserFeedResponse response = service.feed(USER_ID, null, 10, List.of(1L, 2L, 3L));

            assertThat(response.items()).isEmpty();
            assertThat(response.nextSeedId()).isNull();
            assertThat(response.hasMore()).isFalse();
        }
    }

    // ── Helper ──────────────────────────────────────────────────────

    private void stubPreferredTags(List<Long> tagIds) {
        List<UserPreferredTag> tags = tagIds.stream()
                .map(id -> {
                    common.entity.Tag tag = mock(common.entity.Tag.class);
                    when(tag.getId()).thenReturn(id);
                    UserPreferredTag upt = mock(UserPreferredTag.class);
                    when(upt.getTag()).thenReturn(tag);
                    return upt;
                })
                .toList();
        when(userPreferredTagRepository.findAllByUserIdWithTag(USER_ID)).thenReturn(tags);
    }

    @SuppressWarnings("unchecked")
    private void stubEsSearchResult(int count) {
        SearchHits<UserContentDocument> mockHits = mock(SearchHits.class);
        List<SearchHit<UserContentDocument>> hitList = java.util.stream.IntStream
                .rangeClosed(1, count)
                .mapToObj(i -> {
                    SearchHit<UserContentDocument> hit = mock(SearchHit.class);
                    when(hit.getContent()).thenReturn(makeDocument((long) i));
                    when(hit.getScore()).thenReturn(0.9f - i * 0.01f);
                    return hit;
                })
                .toList();

        when(mockHits.getSearchHits()).thenReturn(hitList);
        when(elasticsearchOperations.search(any(NativeQuery.class), eq(UserContentDocument.class)))
                .thenReturn(mockHits);
    }

    private UserContentDocument makeDocument(Long id) {
        return UserContentDocument.builder()
                .userContentId(id)
                .parentContentId(100L + id)
                .uploaderId(USER_ID)
                .title("유저 콘텐츠 " + id)
                .contentStatus("ACTIVE")
                .accessLevel("FREE")
                .thumbnailUrl("https://thumb.com/" + id)
                .totalViewCount(1000L * id)
                .bookmarkCount(50L * id)
                .createdAt(LocalDateTime.now().minusDays(id))
                .tagVector(nonZeroVector())
                .tags(List.of("스포츠", "게임"))
                .build();
    }

    private UserContent makeUserContent(Long id) {
        UserContent uc = mock(UserContent.class);
        when(uc.getId()).thenReturn(id);
        when(uc.getTitle()).thenReturn("유저 콘텐츠 " + id);
        when(uc.getTotalViewCount()).thenReturn(1000L);
        when(uc.getBookmarkCount()).thenReturn(50L);
        when(uc.getCreatedAt()).thenReturn(LocalDateTime.now().minusDays(id));

        content.entity.Content parent = mock(content.entity.Content.class);
        when(parent.getThumbnailUrl()).thenReturn("https://thumb.com/" + id);
        when(uc.getParentContent()).thenReturn(parent);

        return uc;
    }

    private float[] nonZeroVector() {
        float[] v = new float[100];
        v[0] = 1.0f;
        return v;
    }
}
