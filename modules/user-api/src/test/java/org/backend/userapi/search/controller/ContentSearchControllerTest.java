package org.backend.userapi.search.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.backend.userapi.common.exception.GlobalExceptionHandler;
import org.backend.userapi.search.document.ContentDocument;
import org.backend.userapi.search.service.ContentIndexingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ContentSearchControllerTest {

    @Mock
    private ContentIndexingService contentIndexingService;

    @InjectMocks
    private ContentSearchController contentSearchController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(contentSearchController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("전체 재색인(Rebuild) 요청 시 200 OK를 반환한다")
    void rebuildIndex_returnsOk() throws Exception {
        // given
        doNothing().when(contentIndexingService).indexAllContents();

        // when & then
        mockMvc.perform(post("/api/index/rebuild") // 🚨 경로 수정됨
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data").isEmpty()); // Void 응답 확인
    }

    @Test
    @DisplayName("검색어 없이 태그 필터만 있어도 검색 결과를 반환한다")
    void search_withOnlyTag_returnsOk() throws Exception {
        // given
        String tag = "시트콤"; // 키워드는 없고 태그만 있는 상황
        ContentDocument document = ContentDocument.builder()
                .contentId(2L)
                .title("순풍산부인과")
                .tags(List.of("시트콤"))
                .build();

        Page<ContentDocument> page = new PageImpl<>(List.of(document), PageRequest.of(0, 10), 1);
        
        // [수정] 서비스 호출 파라미터 매칭 (keyword는 null)
        when(contentIndexingService.search(eq(null), eq(null), eq(null), eq(tag), any()))
                .thenReturn(page);

        // when & then
        mockMvc.perform(get("/api/search")
                        .queryParam("tag", tag)) // 검색어 없이 tag만 전송
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.contents[0].title").value("순풍산부인과"));
    }

    @Test
    @DisplayName("검색어, 카테고리, 태그가 모두 없을 경우 400 에러를 반환한다")
    void search_everythingMissing_returns400() throws Exception {
        // when & then
    	mockMvc.perform(get("/api/search"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.message").value("검색어나 필터를 하나 이상 입력해주세요.."));
    }

    @Test
    @DisplayName("모든 필터 파라미터를 포함하여 검색 요청 시 정상 처리된다")
    void search_withFullParams_returnsOk() throws Exception {
        // given
        Page<ContentDocument> page = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
        
        when(contentIndexingService.search(any(), any(), any(), any(), any()))
                .thenReturn(page);

        // when & then
        mockMvc.perform(get("/api/search")
                        .queryParam("keyword", "무빙")
                        .queryParam("category", "DRAMA")
                        .queryParam("genre", "ACTION")
                        .queryParam("tag", "초능력")
                        .queryParam("sort", "POPULAR"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("자동완성 요청 시 문자열 리스트를 반환한다")
    void getSuggestions_returnsList() throws Exception {
        // given
        String keyword = "스프";
        List<String> suggestions = List.of("스프링", "스프링부트");
        when(contentIndexingService.getSuggestions(keyword)).thenReturn(suggestions);

        // when & then
        mockMvc.perform(get("/api/search/suggestions")
                        .queryParam("keyword", keyword))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0]").value("스프링"))
                .andExpect(jsonPath("$.data[1]").value("스프링부트"));
    }
    
    @Test
    @DisplayName("인덱싱 상태 조회 요청 시 현재 상태 정보를 반환한다")
    void getIndexingStatus_returnsOk() throws Exception {
        // given
        java.util.Map<String, Object> statusMap = java.util.Map.of(
            "status", "IDLE",
            "lastRunTime", "2024-02-18T10:00:00"
        );
        
        when(contentIndexingService.getIndexingStatus()).thenReturn(statusMap);

        // when & then
        mockMvc.perform(get("/api/index/status")) // 🚨 새로 만든 API 경로 호출
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.status").value("IDLE")); // 서비스가 준 값이 잘 나오는지 확인
    }
}