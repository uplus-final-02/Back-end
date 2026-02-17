package org.backend.userapi.search.controller;

import org.backend.userapi.common.exception.GlobalExceptionHandler;
import org.backend.userapi.search.document.ContentDocument;
import org.backend.userapi.search.service.ContentIndexingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ContentSearchControllerTest {

    @Mock
    private ContentIndexingService contentIndexingService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ContentSearchController controller = new ContentSearchController(contentIndexingService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void rebuildIndex_returnsOk() throws Exception {
        doNothing().when(contentIndexingService).indexAllContents();

        mockMvc.perform(post("/api/contents/index/rebuild")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("성공"));
    }

    @Test
    void indexOne_returnsOk() throws Exception {
        doNothing().when(contentIndexingService).indexContent(3L);

        mockMvc.perform(post("/api/contents/index/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));
    }

    @Test
    void deleteOne_returnsOk() throws Exception {
        doNothing().when(contentIndexingService).deleteContent(3L);

        mockMvc.perform(delete("/api/contents/index/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));
    }

    @Test
    void search_returnsPagedResult() throws Exception {
        ContentDocument document = ContentDocument.builder()
                .contentId(1L)
                .title("테스트 콘텐츠")
                .description("설명")
                .type("SERIES")
                .status("ACTIVE")
                .accessLevel("FREE")
                .totalViewCount(10L)
                .bookmarkCount(2L)
                .build();
        Page<ContentDocument> page = new PageImpl<>(List.of(document), PageRequest.of(0, 20), 1);
        when(contentIndexingService.search(eq("테스트"), any())).thenReturn(page);

        mockMvc.perform(get("/api/contents/search")
                        .queryParam("keyword", "테스트")
                        .queryParam("page", "0")
                        .queryParam("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.contents[0].contentId").value(1))
                .andExpect(jsonPath("$.data.contents[0].title").value("테스트 콘텐츠"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }
}
