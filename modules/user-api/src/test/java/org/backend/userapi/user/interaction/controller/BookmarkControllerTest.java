package org.backend.userapi.user.interaction.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;

import org.backend.userapi.common.exception.BookmarkNotFoundException;
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

import core.security.principal.JwtPrincipal;

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
                        return parameter.getParameterType().equals(JwtPrincipal.class);
                    }

                    @Override
                    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer, 
                                                NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                    	JwtPrincipal mockPrincipal = Mockito.mock(JwtPrincipal.class);
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
    
    @Test
    @DisplayName("찜하기 삭제 요청 시 200 OK와 성공 메시지를 반환한다")
    void removeBookmark_returnsOk() throws Exception {
        // given
        Long contentId = 100L;
        doNothing().when(bookmarkService).removeBookmark(1L, contentId);

        // when & then
        mockMvc.perform(delete("/api/users/me/bookmarks/{contentId}", contentId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("찜 목록에서 삭제되었습니다."));
    }

    @Test
    @DisplayName("찜하지 않은 콘텐츠 삭제 요청 시 404 에러를 반환한다")
    void removeBookmark_throwsNotFoundException() throws Exception {
        // given
        Long contentId = 999L; // 존재하지 않는 콘텐츠 ID 가정
        String errorMessage = "찜하지 않은 콘텐츠입니다.";
        
        // 💡 서비스에서 에러를 던지도록 모킹 (이전에 만든 커스텀 예외 클래스 사용)
        doThrow(new BookmarkNotFoundException(errorMessage))
                .when(bookmarkService).removeBookmark(1L, contentId);

        // when & then
        mockMvc.perform(delete("/api/users/me/bookmarks/{contentId}", contentId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound()) // 404 상태 코드 검증
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(errorMessage));
    }
}