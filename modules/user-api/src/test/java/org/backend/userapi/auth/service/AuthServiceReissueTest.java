package org.backend.userapi.auth.service;

import common.enums.AuthProvider;
import common.enums.UserStatus;
import common.repository.TagRepository;
import org.backend.userapi.auth.dto.LoginResponse;
import org.backend.userapi.auth.dto.ReissueRequest;
import org.backend.userapi.auth.jwt.JwtTokenProvider;
import org.backend.userapi.common.exception.InvalidCredentialsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import user.entity.AuthAccount;
import user.entity.RefreshToken;
import user.entity.User;
import user.repository.AuthAccountRepository;
import user.repository.UserPreferredTagRepository;
import user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceReissueTest {

    @Mock private AuthAccountRepository authAccountRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserPreferredTagRepository userPreferredTagRepository;
    @Mock private TagRepository tagRepository;
    @Mock private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private RefreshTokenService refreshTokenService;

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
    void reissue_success_validatesDbToken_rotates_andReturnsNewTokens() {
        Long userId = 2L;
        String oldRefresh = "old-refresh-token";
        String newRefresh = "new-refresh-token";
        String newAccess = "new-access-token";

        ReissueRequest request = new ReissueRequest(oldRefresh);

        // jwt
        doNothing().when(jwtTokenProvider).validateRefreshToken(oldRefresh);
        when(jwtTokenProvider.extractUserId(oldRefresh)).thenReturn(userId);

        when(jwtTokenProvider.generateRefreshToken(userId)).thenReturn(newRefresh);
        when(jwtTokenProvider.generateAccessToken(any(User.class), eq("test@test.com"))).thenReturn(newAccess);
        when(jwtTokenProvider.getAccessTokenTtlSeconds()).thenReturn(1800L);
        when(jwtTokenProvider.getRefreshTokenTtlSeconds()).thenReturn(1209600L);

        // refresh validation (DB 일치/만료 검증은 service가 수행)
        RefreshToken saved = RefreshToken.builder()
                .userId(userId)
                .token(oldRefresh)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
        when(refreshTokenService.validateAndGet(userId, oldRefresh)).thenReturn(saved);

        // user
        User user = User.builder()
                .nickname("tester")
                .userStatus(UserStatus.ACTIVE)
                .build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // auth account
        AuthAccount authAccount = AuthAccount.builder()
                .user(user)
                .authProvider(AuthProvider.EMAIL)
                .authProviderSubject("test@test.com")
                .email("test@test.com")
                .passwordHash("encoded")
                .build();
        when(authAccountRepository.findByUser_Id(userId)).thenReturn(Optional.of(authAccount));

        LoginResponse response = authService.reissue(request);

        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.accessToken()).isEqualTo(newAccess);
        assertThat(response.refreshToken()).isEqualTo(newRefresh);
        assertThat(response.accessTokenTtlSeconds()).isEqualTo(1800L);
        assertThat(response.refreshTokenTtlSeconds()).isEqualTo(1209600L);

        verify(refreshTokenService).validateAndGet(userId, oldRefresh);
        verify(refreshTokenService).rotate(saved, newRefresh);
    }

    @Test
    void reissue_whenRefreshInvalid_throwsInvalidCredentials() {
        Long userId = 2L;
        String refresh = "bad-refresh";

        ReissueRequest request = new ReissueRequest(refresh);

        doNothing().when(jwtTokenProvider).validateRefreshToken(refresh);
        when(jwtTokenProvider.extractUserId(refresh)).thenReturn(userId);

        when(refreshTokenService.validateAndGet(userId, refresh))
                .thenThrow(new InvalidCredentialsException("리프레시 토큰이 유효하지 않습니다."));

        assertThatThrownBy(() -> authService.reissue(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("리프레시 토큰이 유효하지 않습니다.");

        verify(refreshTokenService).validateAndGet(userId, refresh);
        verify(refreshTokenService, never()).rotate(any(), anyString());
    }
}
