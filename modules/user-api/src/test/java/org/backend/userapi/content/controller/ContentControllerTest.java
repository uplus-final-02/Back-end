package org.backend.userapi.content.controller;

import org.backend.userapi.common.exception.GlobalExceptionHandler;
import org.backend.userapi.content.dto.DefaultContentResponse;
import org.backend.userapi.content.service.ContentService;
import org.backend.userapi.user.service.BookmarkService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
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

    @InjectMocks
    private ContentController contentController;

    @BeforeEach
    void setUp() {
        // Spring Context 없이 직접 컨트롤러를 MockMvc에 등록
        mockMvc = MockMvcBuilders.standaloneSetup(contentController)
                .setControllerAdvice(new GlobalExceptionHandler()) // 실제 핸들러 사용
                .build();
    }


    @Test
    @DisplayName("기본 콘텐츠 목록 조회 - 파라미터 없음")
    @WithMockUser
    void getContents_NoParams() throws Exception {
        // given
        DefaultContentResponse response = DefaultContentResponse.builder()
                .contentId(1L)
                .title("Test Content")
                .build();
        List<DefaultContentResponse> responseList = Collections.singletonList(response);

        given(contentService.getDefaultContents(null, null)).willReturn(responseList);

        // when & then
        mockMvc.perform(get("/api/contents/home/basic")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].contentId").value(1L))
                .andExpect(jsonPath("$.data[0].title").value("Test Content"));
    }

    @Test
    @DisplayName("기본 콘텐츠 목록 조회 - uploaderType 파라미터")
    @WithMockUser
    void getContents_WithUploaderType() throws Exception {
        // given
        String uploaderType = "ADMIN";
        DefaultContentResponse response = DefaultContentResponse.builder()
                .contentId(2L)
                .title("Admin Content")
                .build();
        List<DefaultContentResponse> responseList = Collections.singletonList(response);

        given(contentService.getDefaultContents(eq(uploaderType), eq(null))).willReturn(responseList);

        // when & then
        mockMvc.perform(get("/api/contents/home/basic")
                        .param("uploaderType", uploaderType)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].contentId").value(2L))
                .andExpect(jsonPath("$.data[0].title").value("Admin Content"));
    }

    @Test
    @DisplayName("기본 콘텐츠 목록 조회 - tag 파라미터")
    @WithMockUser
    void getContents_WithTag() throws Exception {
        // given
        String tag = "Action";
        DefaultContentResponse response = DefaultContentResponse.builder()
                .contentId(3L)
                .title("Action Content")
                .build();
        List<DefaultContentResponse> responseList = Collections.singletonList(response);

        given(contentService.getDefaultContents(eq(null), eq(tag))).willReturn(responseList);

        // when & then
        mockMvc.perform(get("/api/contents/home/basic")
                        .param("tag", tag)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].contentId").value(3L))
                .andExpect(jsonPath("$.data[0].title").value("Action Content"));
    }

    @Test
    @DisplayName("기본 콘텐츠 목록 조회 - uploaderType 및 tag 파라미터")
    @WithMockUser
    void getContents_WithBothParams() throws Exception {
        // given
        String uploaderType = "USER";
        String tag = "Comedy";
        DefaultContentResponse response = DefaultContentResponse.builder()
                .contentId(4L)
                .title("User Comedy Content")
                .build();
        List<DefaultContentResponse> responseList = Collections.singletonList(response);

        given(contentService.getDefaultContents(eq(uploaderType), eq(tag))).willReturn(responseList);

        // when & then
        mockMvc.perform(get("/api/contents/home/basic")
                        .param("uploaderType", uploaderType)
                        .param("tag", tag)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].contentId").value(4L))
                .andExpect(jsonPath("$.data[0].title").value("User Comedy Content"));
    }
}
