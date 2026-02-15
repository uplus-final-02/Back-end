package org.backend.userapi.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.backend.userapi.auth.dto.SignupRequest;
import org.backend.userapi.auth.dto.SignupResponse;
import org.backend.userapi.auth.service.AuthService;
import org.backend.userapi.common.exception.DuplicateEmailException;
import org.backend.userapi.common.exception.DuplicateNicknameException;
import org.backend.userapi.common.exception.GlobalExceptionHandler;
import org.backend.userapi.common.exception.InvalidTagException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerExceptionTest {

    @Mock
    private AuthService authService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        AuthController authController = new AuthController(authService);
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();

        objectMapper = new ObjectMapper();
    }

    @Test
    void signup_success_returnsCreatedResponse() throws Exception {
        SignupResponse response = new SignupResponse(1L, "tester", List.of("스포츠", "게임", "뉴스"));
        when(authService.signup(any(SignupRequest.class))).thenReturn(response);

        SignupRequest request = new SignupRequest(
                "test@test.com",
                "password123",
                "tester",
                List.of(1L, 2L, 3L)
        );

        mockMvc.perform(post("/api/auth/signup/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.message").value("생성 완료"))
                .andExpect(jsonPath("$.data.userId").value(1L))
                .andExpect(jsonPath("$.data.nickname").value("tester"));
    }

    @Test
    void signup_duplicateEmail_returnsConflict() throws Exception {
        when(authService.signup(any(SignupRequest.class)))
                .thenThrow(new DuplicateEmailException("이미 사용 중인 이메일입니다."));

        SignupRequest request = new SignupRequest(
                "dup@test.com",
                "password123",
                "tester",
                List.of(1L, 2L, 3L)
        );

        mockMvc.perform(post("/api/auth/signup/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("이미 사용 중인 이메일입니다."))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void signup_duplicateNickname_returnsConflict() throws Exception {
        when(authService.signup(any(SignupRequest.class)))
                .thenThrow(new DuplicateNicknameException("이미 사용 중인 닉네임입니다."));

        SignupRequest request = new SignupRequest(
                "test@test.com",
                "password123",
                "dupNickname",
                List.of(1L, 2L, 3L)
        );

        mockMvc.perform(post("/api/auth/signup/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("이미 사용 중인 닉네임입니다."))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void signup_invalidTag_returnsBadRequest() throws Exception {
        when(authService.signup(any(SignupRequest.class)))
                .thenThrow(new InvalidTagException("선호 태그는 3개 이상 5개 이하로 선택해야 합니다."));

        SignupRequest request = new SignupRequest(
                "test@test.com",
                "password123",
                "tester",
                List.of(1L, 2L, 3L)
        );

        mockMvc.perform(post("/api/auth/signup/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("선호 태그는 3개 이상 5개 이하로 선택해야 합니다."))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void signup_validationFail_returnsBadRequest() throws Exception {
        String invalidRequestJson = """
                {
                  "password": "password123",
                  "nickname": "tester",
                  "tagIds": [1,2,3]
                }
                """;

        mockMvc.perform(post("/api/auth/signup/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("이메일은 필수입니다"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }
}
