package org.backend.userapi.recommendation;

import com.fasterxml.jackson.databind.ObjectMapper;
import core.security.principal.JwtPrincipal;
import org.backend.userapi.common.dto.ApiResponse;
import org.backend.userapi.common.exception.GlobalExceptionHandler;
import org.backend.userapi.recommendation.controller.UserRecommendationController;
import org.backend.userapi.recommendation.dto.UserFeedResponse;
import org.backend.userapi.recommendation.dto.UserRecommendationResponse;
import org.backend.userapi.recommendation.dto.UserRecommendedContentResponse;
import org.backend.userapi.recommendation.service.UserRecommendationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 시나리오 3: 유저가 유저 콘텐츠(숏폼)를 개인화 추천받는다
 * 시나리오 4: 유저가 숏폼 피드를 무한스크롤한다
 *
 * <p>컨트롤러 계층(엔드포인트 라우팅, 파라미터 파싱, 응답 구조)을 검증한다.
 * UserRecommendationService는 mock으로 대체한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("유저 콘텐츠 추천 컨트롤러 시나리오 테스트")
class UserRecommendationControllerScenarioTest {

    private static final Long USER_ID = 42L;

    @Mock
    private UserRecommendationService userRecommendationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        JwtPrincipal mockPrincipal = mock(JwtPrincipal.class);
        when(mockPrincipal.getUserId()).thenReturn(USER_ID);

        UserRecommendationController controller =
                new UserRecommendationController(userRecommendationService);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(principalResolver(mockPrincipal))
                .build();
    }

    // ══════════════════════════════════════════════════════════════
    //  시나리오 3: 개인화 추천
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("시나리오 3 — 유저 콘텐츠 개인화 추천")
    class RecommendScenario {

        @Test
        @DisplayName("기본 추천(extended=false) → 200 OK + items 15개 + hasMore=true")
        void recommended_default_returns15ItemsWithHasMore() throws Exception {
            List<UserRecommendedContentResponse> items = makeItems(15);
            UserRecommendationResponse response = new UserRecommendationResponse(items, true);

            when(userRecommendationService.recommend(USER_ID, false)).thenReturn(response);

            mockMvc.perform(get("/api/user-contents/recommended"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.data.items.length()").value(15))
                    .andExpect(jsonPath("$.data.hasMore").value(true));
        }

        @Test
        @DisplayName("확장 추천(extended=true) → 200 OK + items 50개 + hasMore=false")
        void recommended_extended_returns50ItemsNoHasMore() throws Exception {
            List<UserRecommendedContentResponse> items = makeItems(50);
            UserRecommendationResponse response = new UserRecommendationResponse(items, false);

            when(userRecommendationService.recommend(USER_ID, true)).thenReturn(response);

            mockMvc.perform(get("/api/user-contents/recommended")
                            .param("extended", "true"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items.length()").value(50))
                    .andExpect(jsonPath("$.data.hasMore").value(false));
        }

        @Test
        @DisplayName("0-벡터 Fallback → 200 OK + items 반환 (빈 화면 없음)")
        void recommended_zeroVectorFallback_returnsPopularItems() throws Exception {
            List<UserRecommendedContentResponse> items = makeItems(15);
            UserRecommendationResponse response = new UserRecommendationResponse(items, true);

            // 서비스가 내부적으로 fallback 처리 후 items 반환
            when(userRecommendationService.recommend(USER_ID, false)).thenReturn(response);

            mockMvc.perform(get("/api/user-contents/recommended"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items.length()").value(15));
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  시나리오 4: 숏폼 피드 무한스크롤
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("시나리오 4 — 숏폼 피드 무한스크롤")
    class FeedScenario {

        @Test
        @DisplayName("첫 진입(seedId=null) → 200 OK + items 10개 + nextSeedId + hasMore=true")
        void feed_firstEntry_returnsInitialBatchWithSeedId() throws Exception {
            List<UserRecommendedContentResponse> items = makeItems(10);
            UserFeedResponse response = new UserFeedResponse(items, 10L, true);

            when(userRecommendationService.feed(eq(USER_ID), isNull(), eq(10), eq(List.of())))
                    .thenReturn(response);

            mockMvc.perform(get("/api/user-contents/feed"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items.length()").value(10))
                    .andExpect(jsonPath("$.data.nextSeedId").value(10))
                    .andExpect(jsonPath("$.data.hasMore").value(true));
        }

        @Test
        @DisplayName("스크롤(seedId + excludeIds) → 200 OK + 다음 items + excludeIds 파싱 정확")
        void feed_scroll_parsesExcludeIdsAndReturnNextBatch() throws Exception {
            List<UserRecommendedContentResponse> items = makeItems(10);
            UserFeedResponse response = new UserFeedResponse(items, 20L, true);

            when(userRecommendationService.feed(
                    eq(USER_ID),
                    eq(10L),
                    eq(10),
                    eq(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L))))
                    .thenReturn(response);

            mockMvc.perform(get("/api/user-contents/feed")
                            .param("seedId", "10")
                            .param("size", "10")
                            .param("excludeIds", "1,2,3,4,5,6,7,8,9,10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items.length()").value(10))
                    .andExpect(jsonPath("$.data.nextSeedId").value(20))
                    .andExpect(jsonPath("$.data.hasMore").value(true));
        }

        @Test
        @DisplayName("size=100 요청 → 컨트롤러에서 30으로 클램핑하여 서비스 호출")
        void feed_oversizedRequest_clampsTo30() throws Exception {
            List<UserRecommendedContentResponse> items = makeItems(30);
            UserFeedResponse response = new UserFeedResponse(items, 30L, true);

            // size=100 → 클램핑 → 30으로 서비스 호출되어야 함
            when(userRecommendationService.feed(eq(USER_ID), isNull(), eq(30), eq(List.of())))
                    .thenReturn(response);

            mockMvc.perform(get("/api/user-contents/feed")
                            .param("size", "100"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items.length()").value(30));
        }

        @Test
        @DisplayName("마지막 페이지(items < size) → hasMore=false")
        void feed_lastPage_hasMoreFalse() throws Exception {
            List<UserRecommendedContentResponse> items = makeItems(5); // 요청 10개, 실제 5개
            UserFeedResponse response = new UserFeedResponse(items, null, false);

            when(userRecommendationService.feed(eq(USER_ID), eq(50L), eq(10), anyList()))
                    .thenReturn(response);

            mockMvc.perform(get("/api/user-contents/feed")
                            .param("seedId", "50")
                            .param("excludeIds", "1,2,3,4,5,6,7,8,9,10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items.length()").value(5))
                    .andExpect(jsonPath("$.data.hasMore").value(false));
        }
    }

    // ── Helper ──────────────────────────────────────────────────────

    /** 더미 추천 아이템 생성 */
    private List<UserRecommendedContentResponse> makeItems(int count) {
        return java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj(i -> new UserRecommendedContentResponse(
                        (long) i, 100L + i,
                        "콘텐츠 " + i, "https://thumb.com/" + i,
                        "FREE", 1000L * i, 50L * i, List.of("스포츠")))
                .toList();
    }

    /**
     * @AuthenticationPrincipal JwtPrincipal 을 standaloneSetup 에서 주입하기 위한 커스텀 resolver.
     * Spring Security 없이도 인증 주체(principal)를 컨트롤러에 전달할 수 있다.
     */
    private HandlerMethodArgumentResolver principalResolver(JwtPrincipal principal) {
        return new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.getParameterType().isAssignableFrom(JwtPrincipal.class);
            }

            @Override
            public Object resolveArgument(MethodParameter parameter,
                                          ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest,
                                          WebDataBinderFactory binderFactory) {
                return principal;
            }
        };
    }
}
