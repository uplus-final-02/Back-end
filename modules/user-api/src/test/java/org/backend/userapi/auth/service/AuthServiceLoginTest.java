package org.backend.userapi.auth.service;

import common.enums.AuthProvider;
import common.enums.UserStatus;
import org.backend.userapi.auth.dto.LoginRequest;
import org.backend.userapi.auth.dto.LoginResponse;
import org.backend.userapi.auth.jwt.JwtTokenProvider;
import org.backend.userapi.common.exception.InvalidCredentialsException;
import org.junit.jupiter.api.BeforeEach;
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
import user.repository.RefreshTokenRepository;
import user.repository.UserPreferredTagRepository;
import user.repository.UserRepository;
import common.repository.TagRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import static org.mockito.ArgumentMatchers.eq;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceLoginTest {

    @Mock
    private AuthAccountRepository authAccountRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserPreferredTagRepository userPreferredTagRepository;
    @Mock
    private TagRepository tagRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock 
    private RefreshTokenService refreshTokenService;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;

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
                refreshTokenService
        );
    }

    @Test
    void login_success_returnsTokensAndSavesRefreshToken() {
        User user = User.builder().nickname("tester").build();
        ReflectionTestUtils.setField(user, "id", 1L);

        AuthAccount authAccount = AuthAccount.builder()
                .user(user)
                .authProvider(AuthProvider.EMAIL)
                .authProviderSubject("test@test.com")
                .email("test@test.com")
                .passwordHash("encoded-password")
                .build();

        LoginRequest request = new LoginRequest("test@test.com", "password123");

        when(authAccountRepository.findByAuthProviderAndEmail(AuthProvider.EMAIL, "test@test.com"))
                .thenReturn(Optional.of(authAccount));
        when(passwordEncoder.matches("password123", "encoded-password")).thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(user, "test@test.com")).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken(1L)).thenReturn("refresh-token");
        when(jwtTokenProvider.getAccessTokenTtlSeconds()).thenReturn(1800L);
        when(jwtTokenProvider.getRefreshTokenTtlSeconds()).thenReturn(1209600L);
        
        LoginResponse response = authService.login(request);

        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.accessTokenTtlSeconds()).isEqualTo(1800L);
        assertThat(response.refreshTokenTtlSeconds()).isEqualTo(1209600L);

//        verify(refreshTokenRepository).deleteByUserId(1L);
//        verify(refreshTokenRepository).save(any(RefreshToken.class));
        verify(refreshTokenService).upsert(eq(1L), eq("refresh-token"));
    }

    @Test
    void login_whenEmailNotFound_throwsInvalidCredentials() {
        LoginRequest request = new LoginRequest("missing@test.com", "password123");
        when(authAccountRepository.findByAuthProviderAndEmail(AuthProvider.EMAIL, "missing@test.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("이메일 또는 비밀번호가 올바르지 않습니다.");
    }

    @Test
    void login_whenPasswordMismatch_throwsInvalidCredentials() {
        User user = User.builder().nickname("tester").build();
        ReflectionTestUtils.setField(user, "id", 1L);

        AuthAccount authAccount = AuthAccount.builder()
                .user(user)
                .authProvider(AuthProvider.EMAIL)
                .authProviderSubject("test@test.com")
                .email("test@test.com")
                .passwordHash("encoded-password")
                .build();

        LoginRequest request = new LoginRequest("test@test.com", "wrong-password");
        when(authAccountRepository.findByAuthProviderAndEmail(AuthProvider.EMAIL, "test@test.com"))
                .thenReturn(Optional.of(authAccount));
        when(passwordEncoder.matches("wrong-password", "encoded-password")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("이메일 또는 비밀번호가 올바르지 않습니다.");
    }

    @Test
    void login_whenUserInactive_throwsInvalidCredentials() {
        User user = User.builder()
                .nickname("tester")
                .userStatus(UserStatus.INACTIVE)
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);

        AuthAccount authAccount = AuthAccount.builder()
                .user(user)
                .authProvider(AuthProvider.EMAIL)
                .authProviderSubject("test@test.com")
                .email("test@test.com")
                .passwordHash("encoded-password")
                .build();

        LoginRequest request = new LoginRequest("test@test.com", "password123");
        when(authAccountRepository.findByAuthProviderAndEmail(AuthProvider.EMAIL, "test@test.com"))
                .thenReturn(Optional.of(authAccount));
        when(passwordEncoder.matches("password123", "encoded-password")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("비활성화된 계정입니다.");
    }

    @Test
    void login_whenUserDeleted_throwsInvalidCredentials() {
        User user = User.builder()
                .nickname("tester")
                .userStatus(UserStatus.DELETED)
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);

        AuthAccount authAccount = AuthAccount.builder()
                .user(user)
                .authProvider(AuthProvider.EMAIL)
                .authProviderSubject("test@test.com")
                .email("test@test.com")
                .passwordHash("encoded-password")
                .build();

        LoginRequest request = new LoginRequest("test@test.com", "password123");
        when(authAccountRepository.findByAuthProviderAndEmail(AuthProvider.EMAIL, "test@test.com"))
                .thenReturn(Optional.of(authAccount));
        when(passwordEncoder.matches("password123", "encoded-password")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("비활성화된 계정입니다.");
    }
}
