package org.backend.userapi.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.backend.userapi.auth.dto.LoginResponse;
import org.backend.userapi.auth.dto.ReissueRequest;
import org.backend.userapi.auth.service.AuthService;
import org.backend.userapi.common.exception.GlobalExceptionHandler;
import org.backend.userapi.common.exception.InvalidCredentialsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerReissueTest {

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
    void reissue_success_returnsTokens() throws Exception {
        LoginResponse response = new LoginResponse(
                "Bearer",
                "new-access-token",
                1800,
                "new-refresh-token",
                1209600
        );

        when(authService.reissue(any(ReissueRequest.class))).thenReturn(response);

        ReissueRequest request = new ReissueRequest("old-refresh-token");

        mockMvc.perform(post("/api/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("성공"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("new-refresh-token"))
                .andExpect(jsonPath("$.data.accessTokenTtlSeconds").value(1800))
                .andExpect(jsonPath("$.data.refreshTokenTtlSeconds").value(1209600));
    }

    @Test
    void reissue_invalidRefresh_returnsUnauthorized() throws Exception {
        when(authService.reissue(any(ReissueRequest.class)))
                .thenThrow(new InvalidCredentialsException("리프레시 토큰이 유효하지 않습니다."));

        ReissueRequest request = new ReissueRequest("invalid-refresh-token");

        mockMvc.perform(post("/api/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("리프레시 토큰이 유효하지 않습니다."))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void reissue_validationFail_returnsBadRequest() throws Exception {
        
        String invalidRequestJson = "{}";

        mockMvc.perform(post("/api/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("리프레시 토큰은 필수입니다"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }
}
