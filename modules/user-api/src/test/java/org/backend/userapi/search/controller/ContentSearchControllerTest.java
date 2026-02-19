package org.backend.userapi.search.controller;

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
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
    @DisplayName("검색어가 유효할 경우 검색 결과(DTO 변환 포함)를 반환한다")
    void search_returnsTransformedDto() throws Exception {
        // given
        String keyword = "테스트";
        
        ContentDocument document = ContentDocument.builder()
                .contentId(1L)
                .title("테스트 콘텐츠")
                .description("설명입니다.")
                .highlightTitle("<em>테스트</em> 콘텐츠") 
                .highlightDescription(null)
                .type("SERIES")
                .status("ACTIVE")
                .accessLevel("FREE")
                .thumbnailUrl("http://thumb.url")
                .tags(List.of("드라마", "예능"))
                .totalViewCount(100L)
                .bookmarkCount(5L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Page<ContentDocument> page = new PageImpl<>(List.of(document), PageRequest.of(0, 10), 1);
        
        // any()로 Pageable 매칭
        when(contentIndexingService.search(eq(keyword), any())).thenReturn(page);

        // when & then
        mockMvc.perform(get("/api/search") // 🚨 경로 수정됨
                        .queryParam("keyword", keyword)
                        .queryParam("page", "0")
                        .queryParam("size", "10")
                        .queryParam("sort", "LATEST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.contents[0].contentId").value(1))
                .andExpect(jsonPath("$.data.contents[0].title").value("테스트 콘텐츠"))
                .andExpect(jsonPath("$.data.contents[0].highlightTitle").value("<em>테스트</em> 콘텐츠")) 
                .andExpect(jsonPath("$.data.contents[0].matchType").value("TITLE")) 
                .andExpect(jsonPath("$.data.contents[0].tags[0]").value("드라마"))
                .andExpect(jsonPath("$.data.hasNext").value(false)); 
    }

    @Test
    @DisplayName("검색어가 없을 경우 400 Bad Request 에러를 반환한다")
    void search_emptyKeyword_returns400() throws Exception {
        // when & then
        mockMvc.perform(get("/api/search")
                        .queryParam("keyword", "") 
                        .queryParam("page", "0"))
                .andExpect(status().isBadRequest()) 
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("검색어를 입력해주세요."));
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