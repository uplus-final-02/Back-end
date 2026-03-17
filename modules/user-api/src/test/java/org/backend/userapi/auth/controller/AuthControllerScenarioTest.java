package org.backend.userapi.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.backend.userapi.auth.dto.*;
import org.backend.userapi.auth.oauth.GoogleOAuthService;
import org.backend.userapi.auth.oauth.KakaoOAuthService;
import org.backend.userapi.auth.oauth.NaverOAuthService;
import org.backend.userapi.auth.service.AuthService;
import org.backend.userapi.common.exception.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 시나리오 1: 신규 유저가 이메일로 서비스에 가입한다
 * 시나리오 2: 유저가 로그인하고 인증을 유지한다
 *
 * <p>컨트롤러 계층(HTTP 요청/응답, Bean Validation, 예외 매핑)을 검증한다.
 * AuthService 로직은 mock으로 대체한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Auth 컨트롤러 시나리오 테스트")
class AuthControllerScenarioTest {

    @Mock private AuthService authService;
    @Mock private GoogleOAuthService googleOAuthService;
    @Mock private KakaoOAuthService kakaoOAuthService;
    @Mock private NaverOAuthService naverOAuthService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        AuthController authController = new AuthController(
                authService, googleOAuthService, kakaoOAuthService, naverOAuthService);

        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();

        objectMapper = new ObjectMapper();
    }

    // ══════════════════════════════════════════════════════════════
    //  시나리오 1: 신규 유저가 이메일로 서비스에 가입한다
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("시나리오 1 — 이메일 회원가입 다단계 플로우")
    class SignupFlow {

        @Test
        @DisplayName("STEP 1-A: 인증코드 발송 성공 → 200 OK")
        void sendCode_success_returns200() throws Exception {
            doNothing().when(authService).sendEmailVerificationCode(anyString());

            String body = """
                    { "email": "new@test.com" }
                    """;

            mockMvc.perform(post("/api/auth/signup/email/send-code")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200));
        }

        @Test
        @DisplayName("STEP 1-B: 올바른 인증코드 검증 → Setup Token 발급 200 OK")
        void verifyCode_success_returnsSetupToken() throws Exception {
            SetupTokenResponse response = new SetupTokenResponse("setup-token-abc", 600L);
            when(authService.verifyEmailCode(any(EmailVerifyCodeRequest.class))).thenReturn(response);

            String body = """
                    { "email": "new@test.com", "code": "123456", "password": "password123!" }
                    """;

            mockMvc.perform(post("/api/auth/signup/email/verify-code")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.setupToken").value("setup-token-abc"))
                    .andExpect(jsonPath("$.data.ttlSeconds").value(600));
        }

        @Test
        @DisplayName("STEP 2: 닉네임 설정 → 갱신된 Setup Token 200 OK")
        void setNickname_success_returnsRefreshedSetupToken() throws Exception {
            SetupTokenResponse response = new SetupTokenResponse("setup-token-xyz", 600L);
            when(authService.setNickname(anyString(), any(NicknameSetupRequest.class))).thenReturn(response);

            String body = """
                    { "nickname": "coolUser" }
                    """;

            mockMvc.perform(post("/api/auth/signup/profile/nickname")
                            .header("Authorization", "Bearer setup-token-abc")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.setupToken").value("setup-token-xyz"));
        }

        @Test
        @DisplayName("STEP 3: 태그 선택 → 유저 생성 + JWT 발급 201 Created")
        void completeTags_success_returnsJwt() throws Exception {
            LoginResponse jwt = new LoginResponse("Bearer", "access-token", 1800L,
                    "refresh-token", 1209600L, false, false);
            when(authService.completeSignup(anyString(), any(TagSetupRequest.class))).thenReturn(jwt);

            String body = """
                    { "tagIds": [1, 2, 3] }
                    """;

            mockMvc.perform(post("/api/auth/signup/profile/tags")
                            .header("Authorization", "Bearer setup-token-xyz")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value(201))
                    .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                    .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"));
        }

        // ── 예외 케이스 ────────────────────────────────────────────────

        @Test
        @DisplayName("이미 이메일로 가입된 계정 → 409 Conflict")
        void sendCode_duplicateEmail_returns409() throws Exception {
            doThrow(new DuplicateEmailException("이미 이메일로 가입된 계정입니다."))
                    .when(authService).sendEmailVerificationCode(anyString());

            String body = """
                    { "email": "dup@test.com" }
                    """;

            mockMvc.perform(post("/api/auth/signup/email/send-code")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.message").value("이미 이메일로 가입된 계정입니다."));
        }

        @Test
        @DisplayName("소셜 로그인으로 가입된 이메일 → 409 SocialProviderConflict")
        void sendCode_socialConflict_returns409() throws Exception {
            doThrow(new SocialProviderConflictException("해당 이메일은 Google 계정으로 이미 가입되어 있습니다."))
                    .when(authService).sendEmailVerificationCode(anyString());

            String body = """
                    { "email": "social@test.com" }
                    """;

            mockMvc.perform(post("/api/auth/signup/email/send-code")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409));
        }

        @Test
        @DisplayName("닉네임 중복 → 409 Conflict")
        void setNickname_duplicate_returns409() throws Exception {
            when(authService.setNickname(anyString(), any(NicknameSetupRequest.class)))
                    .thenThrow(new DuplicateNicknameException("이미 사용 중인 닉네임입니다."));

            String body = """
                    { "nickname": "taken" }
                    """;

            mockMvc.perform(post("/api/auth/signup/profile/nickname")
                            .header("Authorization", "Bearer setup-token-abc")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("이미 사용 중인 닉네임입니다."))
                    .andExpect(jsonPath("$.data").value(nullValue()));
        }

        @Test
        @DisplayName("태그 개수 불일치 (3개 미만) → 400 Bad Request")
        void completeTags_tooFewTags_returns400() throws Exception {
            when(authService.completeSignup(anyString(), any(TagSetupRequest.class)))
                    .thenThrow(new InvalidTagException("선호 태그는 3개 이상 5개 이하로 선택해야 합니다."));

            String body = """
                    { "tagIds": [1, 2] }
                    """;

            mockMvc.perform(post("/api/auth/signup/profile/tags")
                            .header("Authorization", "Bearer setup-token-xyz")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value("선호 태그는 3개 이상 5개 이하로 선택해야 합니다."));
        }

        @Test
        @DisplayName("이메일 필드 누락 → 400 Bean Validation")
        void sendCode_missingEmail_returns400() throws Exception {
            String body = "{}";

            mockMvc.perform(post("/api/auth/signup/email/send-code")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.data").value(nullValue()));
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  시나리오 2: 유저가 로그인하고 인증을 유지한다
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("시나리오 2 — 이메일 로그인 · 재발급 · 로그아웃")
    class LoginAndAuthFlow {

        @Test
        @DisplayName("이메일 로그인 성공 → 200 OK + JWT (paid/uplus 포함)")
        void login_success_returnsJwtWithPaidUplus() throws Exception {
            LoginResponse response = new LoginResponse(
                    "Bearer", "access-token", 1800L,
                    "refresh-token", 1209600L, true, false);
            when(authService.login(any(LoginRequest.class))).thenReturn(response);

            String body = """
                    { "email": "user@test.com", "password": "password123!" }
                    """;

            mockMvc.perform(post("/api/auth/login/email")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                    .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"))
                    .andExpect(jsonPath("$.data.paid").value(true))
                    .andExpect(jsonPath("$.data.uplus").value(false));
        }

        @Test
        @DisplayName("존재하지 않는 이메일 → 401 Unauthorized")
        void login_emailNotFound_returns401() throws Exception {
            when(authService.login(any(LoginRequest.class)))
                    .thenThrow(new InvalidCredentialsException("이메일 또는 비밀번호가 올바르지 않습니다."));

            String body = """
                    { "email": "ghost@test.com", "password": "password123!" }
                    """;

            mockMvc.perform(post("/api/auth/login/email")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.message").value("이메일 또는 비밀번호가 올바르지 않습니다."))
                    .andExpect(jsonPath("$.data").value(nullValue()));
        }

        @Test
        @DisplayName("비활성 계정 로그인 시도 → 401 Unauthorized")
        void login_inactiveAccount_returns401() throws Exception {
            when(authService.login(any(LoginRequest.class)))
                    .thenThrow(new InvalidCredentialsException("비활성화된 계정입니다."));

            String body = """
                    { "email": "inactive@test.com", "password": "password123!" }
                    """;

            mockMvc.perform(post("/api/auth/login/email")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("비활성화된 계정입니다."));
        }

        @Test
        @DisplayName("이메일 필드 누락 → 400 Bean Validation")
        void login_missingEmail_returns400() throws Exception {
            String body = """
                    { "password": "password123!" }
                    """;

            mockMvc.perform(post("/api/auth/login/email")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.data").value(nullValue()));
        }

        @Test
        @DisplayName("Refresh Token으로 재발급 성공 → 200 OK + 새 JWT pair")
        void reissue_success_returnsNewTokenPair() throws Exception {
            LoginResponse response = new LoginResponse(
                    "Bearer", "new-access", 1800L,
                    "new-refresh", 1209600L, false, false);
            when(authService.reissue(any(ReissueRequest.class))).thenReturn(response);

            String body = """
                    { "refreshToken": "old-refresh-token" }
                    """;

            mockMvc.perform(post("/api/auth/reissue")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.data.accessToken").value("new-access"))
                    .andExpect(jsonPath("$.data.refreshToken").value("new-refresh"))
                    .andExpect(jsonPath("$.data.accessTokenTtlSeconds").value(1800))
                    .andExpect(jsonPath("$.data.refreshTokenTtlSeconds").value(1209600));
        }

        @Test
        @DisplayName("만료/위변조 Refresh Token → 401 Unauthorized")
        void reissue_invalidToken_returns401() throws Exception {
            when(authService.reissue(any(ReissueRequest.class)))
                    .thenThrow(new InvalidCredentialsException("리프레시 토큰이 유효하지 않습니다."));

            String body = """
                    { "refreshToken": "bad-token" }
                    """;

            mockMvc.perform(post("/api/auth/reissue")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("리프레시 토큰이 유효하지 않습니다."))
                    .andExpect(jsonPath("$.data").value(nullValue()));
        }

        @Test
        @DisplayName("refreshToken 필드 누락 → 400 Bean Validation")
        void reissue_missingToken_returns400() throws Exception {
            mockMvc.perform(post("/api/auth/reissue")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.data").value(nullValue()));
        }

        @Test
        @DisplayName("로그아웃 성공 → 200 OK")
        void logout_success_returns200() throws Exception {
            doNothing().when(authService).logout();

            mockMvc.perform(post("/api/auth/logout"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200));
        }
    }
}
