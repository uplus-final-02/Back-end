package org.backend.userapi.history.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import common.enums.HistoryStatus;
import org.backend.userapi.common.exception.GlobalExceptionHandler;
import org.backend.userapi.common.exception.VideoNotFoundException;
import org.backend.userapi.history.dto.SavePointRequest;
import org.backend.userapi.history.dto.SavePointResponse;
import org.backend.userapi.history.service.HistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.*;

@ExtendWith(MockitoExtension.class)
public class HistoryControllerTest {
    private MockMvc mockMvc;

    @Mock
    private HistoryService historyService;

    @InjectMocks
    private HistoryController historyController;

    @BeforeEach
    void setUp() {
        // Spring Context 없이 직접 컨트롤러를 MockMvc에 등록
        mockMvc = MockMvcBuilders.standaloneSetup(historyController)
                                 .setControllerAdvice(new GlobalExceptionHandler()) // 실제 핸들러 사용
                                 .build();
    }

    @Test
    @DisplayName("시청 기록 저장 성공 테스트")
    @WithMockUser
    void savePoint_success() throws Exception {
        // given
        Long videoId = 10L;
        SavePointRequest request = new SavePointRequest(120, 60);
        SavePointResponse response = new SavePointResponse(50L, HistoryStatus.WATCHING, 120);

        given(historyService.savePoint(any(), eq(videoId), any(SavePointRequest.class)))
                  .willReturn(response);

        // when & then
        mockMvc.perform(post("/api/histories/savepoint/{videoId}", videoId)
                   .contentType(MediaType.APPLICATION_JSON)
                   .content(new ObjectMapper().writeValueAsString(request)))
               .andDo(print())
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.status").value(200))
               .andExpect(jsonPath("$.data.historyId").value(50L));
    }

    @Test
    @DisplayName("유효성 검사 실패: 재생 시간이 음수면 400 Bad Request 반환")
    void savePoint_validation_fail() throws Exception {
        // given
        Long videoId = 10L;
        // positionSec에 음수(-1)를 넣음 -> @Min 위반
        SavePointRequest invalidRequest = new SavePointRequest(-1, 60);

        // when & then
        mockMvc.perform(post("/api/histories/savepoint/{videoId}", videoId)
                   .contentType(MediaType.APPLICATION_JSON)
                   .content(new ObjectMapper().writeValueAsString(invalidRequest)))
               .andDo(print())
               .andExpect(status().isBadRequest()); // 400 에러가 터져야 성공
    }

    @Test
    @DisplayName("서비스 예외 발생: 비디오를 찾을 수 없으면 404 Not Found 반환")
    void savePoint_service_exception() throws Exception {
        // given
        Long videoId = 999L;
        SavePointRequest request = new SavePointRequest(120, 60);

        willThrow(new VideoNotFoundException("존재하지 않는 비디오입니다."))
            .given(historyService)
            .savePoint(any(), eq(videoId), any(SavePointRequest.class));

        // when & then
        mockMvc.perform(post("/api/histories/savepoint/{videoId}", videoId)
                   .contentType(MediaType.APPLICATION_JSON)
                   .content(new ObjectMapper().writeValueAsString(request)))
               .andDo(print())
               // ★ NotFound (404)
               .andExpect(status().isNotFound())
               // (선택) 실제로 내가 던진 예외가 맞는지 확인하려면 아래 줄 추가
               .andExpect(result -> {
                   if (result.getResolvedException() instanceof VideoNotFoundException) {
                       return; // 통과
                   }
                   // 예외 핸들러가 없어서 404로 변환되지 않았더라도, 예외 타입은 맞아야 함
                   throw new AssertionError("기대했던 VideoNotFoundException이 발생하지 않았습니다.");
               });
    }
}
