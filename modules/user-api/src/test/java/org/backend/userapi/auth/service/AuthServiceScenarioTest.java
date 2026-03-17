package org.backend.userapi.auth.service;

import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import common.entity.Tag;
import common.enums.AuthProvider;
import common.enums.UserStatus;
import common.repository.TagRepository;
import core.security.jwt.JwtTokenProvider;
import org.backend.userapi.auth.dto.*;
import org.backend.userapi.common.exception.DuplicateNicknameException;
import org.backend.userapi.common.exception.InvalidCredentialsException;
import org.backend.userapi.common.exception.InvalidSetupTokenException;
import org.backend.userapi.common.exception.InvalidTagException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import user.entity.AuthAccount;
import user.entity.RefreshToken;
import user.entity.User;
import user.repository.AuthAccountRepository;
import user.repository.UserPreferredTagRepository;
import user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 시나리오 1: 신규 유저가 이메일로 서비스에 가입한다 (서비스 계층)
 * 시나리오 2: 유저가 로그인하고 인증을 유지한다 (서비스 계층)
 *
 * <p>AuthService 비즈니스 로직, Rate Limit 연동, JWT 발급, DB 저장 흐름을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Auth 서비스 시나리오 테스트")
class AuthServiceScenarioTest {

    @Mock private AuthAccountRepository authAccountRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserPreferredTagRepository userPreferredTagRepository;
    @Mock private TagRepository tagRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private EmailVerificationService emailVerificationService;
    @Mock private MembershipCheckService membershipCheckService;
    @Mock private LoginRateLimitService loginRateLimitService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                authAccountRepository,
                userRepository,
                userPreferredTagRepository,
                tagRepository,
                passwordEncoder,
                jwtTokenProvider,
                refreshTokenService,
                emailVerificationService,
                membershipCheckService,
                loginRateLimitService
        );
    }

    // ══════════════════════════════════════════════════════════════
    //  시나리오 1: 신규 유저가 이메일로 서비스에 가입한다
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("시나리오 1 — 이메일 회원가입 (서비스)")
    class SignupScenario {

        @Test
        @DisplayName("STEP 3 completeSignup: Setup Token 정보로 User/AuthAccount 생성 + JWT 발급")
        void completeSignup_validSetupToken_createsUserAndIssuesJwt() {
            // Setup Token 디코딩 결과 mock
            DecodedJWT decoded = mock(DecodedJWT.class);
            mockClaim(decoded, "provider", "EMAIL");
            mockClaim(decoded, "email", "new@test.com");
            mockClaim(decoded, "passwordHash", "hashed-pw");
            mockClaim(decoded, "providerSubject", null);
            mockClaim(decoded, "nickname", "coolUser");

            when(jwtTokenProvider.validateAndGetSetupToken("setup-token")).thenReturn(decoded);

            // 태그 검증
            Tag tag1 = Tag.builder().name("스포츠").build();
            Tag tag2 = Tag.builder().name("게임").build();
            Tag tag3 = Tag.builder().name("뉴스").build();
            when(tagRepository.findAllByIdIn(List.of(1L, 2L, 3L)))
                    .thenReturn(List.of(tag1, tag2, tag3));

            // User 저장 시 ID 주입
            User savedUser = User.builder().nickname("coolUser").build();
            ReflectionTestUtils.setField(savedUser, "id", 10L);
            when(userRepository.save(any(User.class))).thenReturn(savedUser);

            // JWT 발급 mock
            when(jwtTokenProvider.generateAccessToken(eq(10L), eq("new@test.com"),
                    eq("coolUser"), anyString(), anyBoolean(), anyBoolean()))
                    .thenReturn("access-token");
            when(jwtTokenProvider.generateRefreshToken(10L)).thenReturn("refresh-token");
            when(jwtTokenProvider.getAccessTokenTtlSeconds()).thenReturn(1800L);
            when(jwtTokenProvider.getRefreshTokenTtlSeconds()).thenReturn(1209600L);

            TagSetupRequest request = new TagSetupRequest(List.of(1L, 2L, 3L));
            LoginResponse response = authService.completeSignup("setup-token", request);

            assertThat(response.tokenType()).isEqualTo("Bearer");
            assertThat(response.accessToken()).isEqualTo("access-token");
            assertThat(response.refreshToken()).isEqualTo("refresh-token");

            verify(userRepository).save(any(User.class));
            verify(authAccountRepository).save(any(AuthAccount.class));
            verify(userPreferredTagRepository).saveAll(anyList());
            verify(refreshTokenService).upsert(eq(10L), eq("refresh-token"));
        }

        @Test
        @DisplayName("STEP 3 completeSignup: 닉네임 미설정 Setup Token → InvalidSetupTokenException")
        void completeSignup_missingNickname_throwsInvalidSetupToken() {
            DecodedJWT decoded = mock(DecodedJWT.class);
            mockClaim(decoded, "provider", "EMAIL");
            mockClaim(decoded, "email", "new@test.com");
            mockClaim(decoded, "passwordHash", "hashed-pw");
            mockClaim(decoded, "providerSubject", null);
            mockClaim(decoded, "nickname", null); // 닉네임 미설정

            when(jwtTokenProvider.validateAndGetSetupToken("setup-token-no-nick")).thenReturn(decoded);

            TagSetupRequest request = new TagSetupRequest(List.of(1L, 2L, 3L));

            assertThatThrownBy(() -> authService.completeSignup("setup-token-no-nick", request))
                    .isInstanceOf(InvalidSetupTokenException.class)
                    .hasMessage("닉네임 설정 단계를 먼저 완료해주세요.");
        }

        @Test
        @DisplayName("STEP 3 completeSignup: 태그 3개 미만 → InvalidTagException")
        void completeSignup_tooFewTags_throwsInvalidTag() {
            DecodedJWT decoded = mock(DecodedJWT.class);
            mockClaim(decoded, "provider", "EMAIL");
            mockClaim(decoded, "email", "new@test.com");
            mockClaim(decoded, "passwordHash", "hashed-pw");
            mockClaim(decoded, "providerSubject", null);
            mockClaim(decoded, "nickname", "coolUser");

            when(jwtTokenProvider.validateAndGetSetupToken("setup-token")).thenReturn(decoded);

            TagSetupRequest request = new TagSetupRequest(List.of(1L, 2L)); // 2개 → 오류

            assertThatThrownBy(() -> authService.completeSignup("setup-token", request))
                    .isInstanceOf(InvalidTagException.class)
                    .hasMessage("선호 태그는 3개 이상 5개 이하로 선택해야 합니다.");
        }

        @Test
        @DisplayName("STEP 2 setNickname: 닉네임 중복 → DuplicateNicknameException")
        void setNickname_duplicate_throwsDuplicateNickname() {
            DecodedJWT decoded = mock(DecodedJWT.class);
            mockClaim(decoded, "provider", "EMAIL");
            mockClaim(decoded, "email", "new@test.com");
            mockClaim(decoded, "passwordHash", "hashed-pw");
            mockClaim(decoded, "providerSubject", null);
            mockClaim(decoded, "nickname", null);

            when(jwtTokenProvider.validateAndGetSetupToken("setup-token")).thenReturn(decoded);
            when(userRepository.existsByNickname("taken")).thenReturn(true);

            NicknameSetupRequest request = new NicknameSetupRequest("taken");

            assertThatThrownBy(() -> authService.setNickname("setup-token", request))
                    .isInstanceOf(DuplicateNicknameException.class)
                    .hasMessage("이미 사용 중인 닉네임입니다.");
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  시나리오 2-A: 이메일 로그인
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("시나리오 2-A — 이메일 로그인")
    class LoginScenario {

        private User activeUser;
        private AuthAccount authAccount;

        @BeforeEach
        void prepareAccount() {
            activeUser = User.builder().nickname("tester").build();
            ReflectionTestUtils.setField(activeUser, "id", 1L);

            authAccount = AuthAccount.builder()
                    .user(activeUser)
                    .authProvider(AuthProvider.EMAIL)
                    .authProviderSubject("user@test.com")
                    .email("user@test.com")
                    .passwordHash("hashed-pw")
                    .build();
        }

        @Test
        @DisplayName("정상 로그인 → JWT 발급 + Refresh Token upsert")
        void login_success_issuesJwtAndUpsertsRefreshToken() {
            when(authAccountRepository.findByAuthProviderAndEmail(AuthProvider.EMAIL, "user@test.com"))
                    .thenReturn(Optional.of(authAccount));
            when(passwordEncoder.matches("password123!", "hashed-pw")).thenReturn(true);
            when(loginRateLimitService.acquireProcessingLock("user@test.com")).thenReturn(true);
            when(jwtTokenProvider.generateAccessToken(eq(1L), eq("user@test.com"),
                    eq("tester"), anyString(), anyBoolean(), anyBoolean()))
                    .thenReturn("access-token");
            when(jwtTokenProvider.generateRefreshToken(1L)).thenReturn("refresh-token");
            when(jwtTokenProvider.getAccessTokenTtlSeconds()).thenReturn(1800L);
            when(jwtTokenProvider.getRefreshTokenTtlSeconds()).thenReturn(1209600L);

            LoginResponse response = authService.login(new LoginRequest("user@test.com", "password123!"));

            assertThat(response.tokenType()).isEqualTo("Bearer");
            assertThat(response.accessToken()).isEqualTo("access-token");
            assertThat(response.refreshToken()).isEqualTo("refresh-token");

            verify(refreshTokenService).upsert(eq(1L), eq("refresh-token"));
            verify(loginRateLimitService).clearFailure("user@test.com");
        }

        @Test
        @DisplayName("존재하지 않는 이메일 → InvalidCredentialsException + 실패 카운트 증가")
        void login_emailNotFound_throwsAndRecordsFailure() {
            when(authAccountRepository.findByAuthProviderAndEmail(AuthProvider.EMAIL, "ghost@test.com"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(new LoginRequest("ghost@test.com", "pw")))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessage("이메일 또는 비밀번호가 올바르지 않습니다.");

            verify(loginRateLimitService).recordFailure("ghost@test.com");
        }

        @Test
        @DisplayName("비밀번호 불일치 → InvalidCredentialsException + 실패 카운트 증가")
        void login_wrongPassword_throwsAndRecordsFailure() {
            when(authAccountRepository.findByAuthProviderAndEmail(AuthProvider.EMAIL, "user@test.com"))
                    .thenReturn(Optional.of(authAccount));
            when(passwordEncoder.matches("wrong-pw", "hashed-pw")).thenReturn(false);
            when(loginRateLimitService.acquireProcessingLock("user@test.com")).thenReturn(true);

            assertThatThrownBy(() -> authService.login(new LoginRequest("user@test.com", "wrong-pw")))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessage("이메일 또는 비밀번호가 올바르지 않습니다.");

            verify(loginRateLimitService).recordFailure("user@test.com");
        }

        @Test
        @DisplayName("비활성 계정(INACTIVE) → InvalidCredentialsException")
        void login_inactiveUser_throws() {
            User inactiveUser = User.builder()
                    .nickname("tester")
                    .userStatus(UserStatus.INACTIVE)
                    .build();
            ReflectionTestUtils.setField(inactiveUser, "id", 1L);

            AuthAccount inactiveAccount = AuthAccount.builder()
                    .user(inactiveUser)
                    .authProvider(AuthProvider.EMAIL)
                    .authProviderSubject("user@test.com")
                    .email("user@test.com")
                    .passwordHash("hashed-pw")
                    .build();

            when(authAccountRepository.findByAuthProviderAndEmail(AuthProvider.EMAIL, "user@test.com"))
                    .thenReturn(Optional.of(inactiveAccount));
            when(passwordEncoder.matches("password123!", "hashed-pw")).thenReturn(true);
            when(loginRateLimitService.acquireProcessingLock("user@test.com")).thenReturn(true);

            assertThatThrownBy(() -> authService.login(new LoginRequest("user@test.com", "password123!")))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessage("비활성화된 계정입니다.");
        }

        @Test
        @DisplayName("삭제된 계정(DELETED) → InvalidCredentialsException")
        void login_deletedUser_throws() {
            User deletedUser = User.builder()
                    .nickname("tester")
                    .userStatus(UserStatus.DELETED)
                    .build();
            ReflectionTestUtils.setField(deletedUser, "id", 1L);

            AuthAccount deletedAccount = AuthAccount.builder()
                    .user(deletedUser)
                    .authProvider(AuthProvider.EMAIL)
                    .authProviderSubject("user@test.com")
                    .email("user@test.com")
                    .passwordHash("hashed-pw")
                    .build();

            when(authAccountRepository.findByAuthProviderAndEmail(AuthProvider.EMAIL, "user@test.com"))
                    .thenReturn(Optional.of(deletedAccount));
            when(passwordEncoder.matches("password123!", "hashed-pw")).thenReturn(true);
            when(loginRateLimitService.acquireProcessingLock("user@test.com")).thenReturn(true);

            assertThatThrownBy(() -> authService.login(new LoginRequest("user@test.com", "password123!")))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessage("비활성화된 계정입니다.");
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  시나리오 2-B: 토큰 재발급
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("시나리오 2-B — 토큰 재발급")
    class ReissueScenario {

        @Test
        @DisplayName("정상 재발급 → 새 JWT pair 발급 + refreshToken upsert")
        void reissue_success_issuesNewTokenPairAndUpserts() {
            Long userId = 2L;
            String oldRefresh = "old-refresh-token";
            String newAccess = "new-access-token";
            String newRefresh = "new-refresh-token";

            when(jwtTokenProvider.extractUserIdFromRefreshToken(oldRefresh)).thenReturn(userId);
            when(loginRateLimitService.acquireReissueLock(userId)).thenReturn(true);

            RefreshToken saved = RefreshToken.builder()
                    .userId(userId)
                    .token(oldRefresh)
                    .expiresAt(LocalDateTime.now().plusDays(7))
                    .build();
            when(refreshTokenService.validateStoredTokenAndGet(userId, oldRefresh)).thenReturn(saved);

            User user = User.builder().nickname("tester").userStatus(UserStatus.ACTIVE).build();
            ReflectionTestUtils.setField(user, "id", userId);
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            AuthAccount authAccount = AuthAccount.builder()
                    .user(user)
                    .authProvider(AuthProvider.EMAIL)
                    .email("user@test.com")
                    .build();
            when(authAccountRepository.findByUser_Id(userId)).thenReturn(Optional.of(authAccount));

            when(jwtTokenProvider.generateAccessToken(eq(userId), eq("user@test.com"),
                    eq("tester"), anyString(), anyBoolean(), anyBoolean()))
                    .thenReturn(newAccess);
            when(jwtTokenProvider.generateRefreshToken(userId)).thenReturn(newRefresh);
            when(jwtTokenProvider.getAccessTokenTtlSeconds()).thenReturn(1800L);
            when(jwtTokenProvider.getRefreshTokenTtlSeconds()).thenReturn(1209600L);

            LoginResponse response = authService.reissue(new ReissueRequest(oldRefresh));

            assertThat(response.accessToken()).isEqualTo(newAccess);
            assertThat(response.refreshToken()).isEqualTo(newRefresh);

            verify(refreshTokenService).validateStoredTokenAndGet(userId, oldRefresh);
            verify(refreshTokenService).upsert(userId, newRefresh);
        }

        @Test
        @DisplayName("저장된 토큰과 불일치 → InvalidCredentialsException + upsert 미호출")
        void reissue_invalidToken_throwsAndSkipsUpsert() {
            Long userId = 2L;
            String badRefresh = "bad-refresh-token";

            when(jwtTokenProvider.extractUserIdFromRefreshToken(badRefresh)).thenReturn(userId);
            when(loginRateLimitService.acquireReissueLock(userId)).thenReturn(true);
            when(refreshTokenService.validateStoredTokenAndGet(userId, badRefresh))
                    .thenThrow(new InvalidCredentialsException("리프레시 토큰이 유효하지 않습니다."));

            assertThatThrownBy(() -> authService.reissue(new ReissueRequest(badRefresh)))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessage("리프레시 토큰이 유효하지 않습니다.");

            verify(refreshTokenService, never()).upsert(anyLong(), anyString());
        }
    }

    // ── Helper ──────────────────────────────────────────────────────

    private void mockClaim(DecodedJWT decoded, String claimName, String value) {
        Claim claim = mock(Claim.class);
        when(claim.asString()).thenReturn(value);
        when(decoded.getClaim(claimName)).thenReturn(claim);
    }
}
