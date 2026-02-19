package org.backend.userapi.user.interaction.controller;

import org.backend.userapi.auth.dto.UserPrincipal;
import org.backend.userapi.common.exception.GlobalExceptionHandler;
import org.backend.userapi.user.controller.BookmarkController;
import org.backend.userapi.user.dto.response.BookmarkListResponse;
import org.backend.userapi.user.service.BookmarkService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class BookmarkControllerTest {

    @Mock
    private BookmarkService bookmarkService;

    @InjectMocks
    private BookmarkController bookmarkController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(bookmarkController)
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.getParameterType().equals(UserPrincipal.class);
                    }

                    @Override
                    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer, 
                                                NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                        UserPrincipal mockPrincipal = Mockito.mock(UserPrincipal.class);
                        Mockito.when(mockPrincipal.getUserId()).thenReturn(1L);
                        return mockPrincipal;
                    }
                })
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("찜하기 등록 요청 시 200 OK를 반환한다")
    void addBookmark_returnsOk() throws Exception {
        Long contentId = 100L;
        doNothing().when(bookmarkService).addBookmark(1L, contentId);

        mockMvc.perform(post("/api/histories/bookmarks/{contentId}", contentId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("Success"));
    }

    @Test
    @DisplayName("찜 목록 조회 시 데이터를 반환한다")
    void getBookmarks_returnsList() throws Exception {
        // 🚨 중요: 실제 레코드 정의(bookmarks, nextCursor, hasNext, totalCount) 순서를 맞춰야 합니다.
        BookmarkListResponse mockResponse = new BookmarkListResponse(
                Collections.emptyList(), null, false, 0L
        );
        
        when(bookmarkService.getMyBookmarks(eq(1L), any(), anyInt()))
                .thenReturn(mockResponse);

        mockMvc.perform(get("/api/users/me/bookmarks")
                        .param("size", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("찜 목록 조회 성공"));
    }
}