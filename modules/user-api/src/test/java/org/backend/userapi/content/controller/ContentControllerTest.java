package org.backend.userapi.content.controller;

import core.security.principal.JwtPrincipal;
import org.backend.userapi.common.exception.GlobalExceptionHandler;
import org.backend.userapi.content.dto.ContentDetailResponse;
import org.backend.userapi.content.dto.DefaultContentResponse;
import org.backend.userapi.content.dto.EpisodeResponse;
import org.backend.userapi.content.dto.EpisodesResponse;
import org.backend.userapi.content.service.ContentService;
import org.backend.userapi.user.service.BookmarkService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ContentControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ContentService contentService;

    @Mock
    private BookmarkService bookmarkService;

    @InjectMocks
    private ContentController contentController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(contentController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new MockJwtPrincipalArgumentResolver())
                .build();
    }

    private static class MockJwtPrincipalArgumentResolver implements HandlerMethodArgumentResolver {
        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.getParameterType().equals(JwtPrincipal.class);
        }

        @Override
        public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer, NativeWebRequest webRequest, WebDataBinderFactory binderFactory) throws Exception {
            return new JwtPrincipal(1L);
        }
    }

    @Test
    @DisplayName("시청 중인 콘텐츠 목록 조회")
    @WithMockUser
    void getWatchingContentList() throws Exception {
        // given
        DefaultContentResponse response = DefaultContentResponse.builder()
                .contentId(10L)
                .title("Watching Content")
                .build();
        List<DefaultContentResponse> responseList = Collections.singletonList(response);

        given(contentService.getWatchingContents(eq(1L))).willReturn(responseList);

        // when & then
        mockMvc.perform(get("/api/contents/home/watching-list")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].contentId").value(10L))
                .andExpect(jsonPath("$.data[0].title").value("Watching Content"));
    }

    @Test
    @DisplayName("최근 찜 목록 조회")
    @WithMockUser
    void getBookmarkList() throws Exception {
        // given
        DefaultContentResponse response = DefaultContentResponse.builder()
                .contentId(20L)
                .title("Bookmarked Content")
                .build();
        List<DefaultContentResponse> responseList = Collections.singletonList(response);

        given(bookmarkService.getRecentBookmarkList(eq(1L))).willReturn(responseList);

        // when & then
        mockMvc.perform(get("/api/contents/home/bookmark-list")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].contentId").value(20L))
                .andExpect(jsonPath("$.data[0].title").value("Bookmarked Content"));
    }

    @Test
    @DisplayName("콘텐츠 상세 조회")
    @WithMockUser
    void getContentDetail() throws Exception {
        // given
        Long contentId = 100L;
        ContentDetailResponse response = ContentDetailResponse.builder()
                .contentId(contentId)
                .title("Detail Content")
                .build();

        given(contentService.getContentDetail(contentId)).willReturn(response);

        // when & then
        mockMvc.perform(get("/api/contents/{contentId}", contentId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contentId").value(contentId))
                .andExpect(jsonPath("$.title").value("Detail Content"));
    }

    @Test
    @DisplayName("에피소드 목록 조회")
    @WithMockUser
    void getContentEpisodeList() throws Exception {
        // given
        Long contentId = 200L;
        EpisodeResponse episode = EpisodeResponse.builder()
                .videoId(1L)
                .title("Episode 1")
                .build();
        EpisodesResponse response = EpisodesResponse.of(contentId, Collections.singletonList(episode));

        given(contentService.getContentEpisodes(contentId)).willReturn(response);

        // when & then
        mockMvc.perform(get("/api/contents/{contentId}/episodes-list", contentId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contentId").value(contentId))
                .andExpect(jsonPath("$.episodes[0].title").value("Episode 1"));
    }
}