package org.backend.userapi.membership.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.backend.userapi.common.exception.GlobalExceptionHandler;
import org.backend.userapi.membership.dto.UplusVerificationRequest;
import org.backend.userapi.membership.dto.UplusVerificationResponse;
import org.backend.userapi.membership.exception.UplusUserNotFoundException;
import org.backend.userapi.membership.service.UplusMembershipService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;
import java.time.LocalDateTime;

import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
public class UplusMembershipControllerTest {

    private MockMvc mockMvc;

    @Mock
    private UplusMembershipService uplusMembershipService;

    @InjectMocks
    private UplusMembershipController uplusMembershipController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(uplusMembershipController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("U+ 회원 인증 성공 테스트")
    void verify_success() throws Exception {
        // given
        Long userId = 2L;
        String phoneNumber = "01012345678";
        LocalDateTime verifiedAt = LocalDateTime.of(2026, 2, 26, 3, 30, 53);

        // ✅ Request DTO 객체 만들지 말고 JSON 바디를 직접 만든다
        ObjectNode body = objectMapper.createObjectNode();
        body.put("phoneNumber", phoneNumber);

        UplusVerificationResponse response = UplusVerificationResponse.builder()
                .isVerified(true) // ✅ isVerified 필드 기준
                .phoneNumber(phoneNumber)
                .verifiedAt(verifiedAt)
                .build();

        given(uplusMembershipService.verify(eq(userId), any(UplusVerificationRequest.class)))
                .willReturn(response);

        Principal principal = new UsernamePasswordAuthenticationToken(String.valueOf(userId), null);

        // when & then
        mockMvc.perform(post("/api/membership/uplus/verify") // ✅ 실제 경로 맞춰
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andDo(print())
                .andExpect(status().isOk())

                // ✅ 현재 너희 응답이 이중 래핑 상태라면(네가 올린 그대로) 아래처럼 검증
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.data.phoneNumber").value(phoneNumber))
                .andExpect(jsonPath("$.data.data.verified").value(true))
                .andExpect(jsonPath("$.data.data.verifiedAt").exists());
    }

    @Test
    @DisplayName("유효성 검사 실패: phoneNumber가 빈값이면 400 Bad Request")
    void verify_validation_fail() throws Exception {
        // given
        Long userId = 2L;
        ObjectNode body = objectMapper.createObjectNode();
        body.put("phoneNumber", ""); // @NotBlank 위반 전제

        Principal principal = new UsernamePasswordAuthenticationToken(String.valueOf(userId), null);

        // when & then
        mockMvc.perform(post("/api/membership/uplus/verify")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("서비스 예외: U+ 회원이 아니면 4xx 반환 + 예외 타입 확인")
    void verify_service_exception() throws Exception {
        // given
        Long userId = 2L;
        ObjectNode body = objectMapper.createObjectNode();
        body.put("phoneNumber", "01000000000");

        willThrow(new UplusUserNotFoundException("LG U+ 회원이 아닙니다."))
                .given(uplusMembershipService)
                .verify(eq(userId), any(UplusVerificationRequest.class));

        Principal principal = new UsernamePasswordAuthenticationToken(String.valueOf(userId), null);

        // when & then
        mockMvc.perform(post("/api/membership/uplus/verify")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andDo(print())
                .andExpect(status().is4xxClientError())
                .andExpect(result -> {
                    if (result.getResolvedException() instanceof UplusUserNotFoundException) {
                        return;
                    }
                    throw new AssertionError("기대했던 UplusUserNotFoundException이 발생하지 않았습니다.");
                });
    }
}