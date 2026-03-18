package org.backend.userapi.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.backend.userapi.auth.dto.*;
import org.backend.userapi.auth.oauth.GoogleOAuthService;
import org.backend.userapi.auth.oauth.KakaoOAuthService;
import org.backend.userapi.auth.oauth.NaverOAuthService;
import org.backend.userapi.auth.oauth.OAuthUserInfo;
import org.backend.userapi.auth.service.AuthService;
import org.backend.userapi.common.dto.ApiResponse;
import common.enums.AuthProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "인증 API", description = "이메일·소셜 회원가입, 로그인, 토큰 재발급, 로그아웃")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final GoogleOAuthService googleOAuthService;
    private final KakaoOAuthService kakaoOAuthService;
    private final NaverOAuthService naverOAuthService;

    // ══════════════════════════════════════════════════════════════
    //  이메일 회원가입 - 다단계
    // ══════════════════════════════════════════════════════════════

    /**
     * STEP 1-A: 인증코드 발송
     * POST /api/auth/signup/email/send-code
     */
    @Operation(summary = "[Step 1-A] 이메일 인증 코드 발송", description = "입력한 이메일로 6자리 인증 코드를 발송합니다. 코드 유효 시간은 5분입니다.")
    @PostMapping("/signup/email/send-code")
    public ResponseEntity<ApiResponse<Void>> sendEmailCode(
            @Valid @RequestBody EmailSendCodeRequest request) {

        authService.sendEmailVerificationCode(request.email());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * STEP 1-B: 인증코드 검증 → Setup Token 발급
     * POST /api/auth/signup/email/verify-code
     */
    @Operation(summary = "[Step 1-B] 이메일 인증 코드 검증", description = "발송된 코드를 검증하고 회원가입 진행용 Setup Token을 발급합니다. (유효시간 10분)")
    @PostMapping("/signup/email/verify-code")
    public ResponseEntity<ApiResponse<SetupTokenResponse>> verifyEmailCode(
            @Valid @RequestBody EmailVerifyCodeRequest request) {

        SetupTokenResponse response = authService.verifyEmailCode(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // ══════════════════════════════════════════════════════════════
    //  소셜 로그인 (Google / Kakao / Naver)
    // ══════════════════════════════════════════════════════════════

    /**
     * Google 소셜 로그인
     * POST /api/auth/login/google
     * - 기존 유저: JWT 반환 (isNewUser=false)
     * - 신규 유저: Setup Token 반환 (isNewUser=true) → 닉네임/태그 단계로 이동
     */
    @Operation(summary = "Google 소셜 로그인", description = "Google OAuth 인가 코드로 로그인합니다. 신규 유저는 Setup Token을 반환하고 닉네임 설정 단계로 이동합니다.")
    @PostMapping("/login/google")
    public ResponseEntity<ApiResponse<SocialLoginResponse>> loginWithGoogle(
            @Valid @RequestBody SocialLoginRequest request) {

        OAuthUserInfo userInfo = googleOAuthService.getUserInfo(request.code(), request.redirectUri());
        SocialLoginResponse response = authService.socialLogin(AuthProvider.GOOGLE, userInfo);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Kakao 소셜 로그인
     * POST /api/auth/login/kakao
     */
    @Operation(summary = "Kakao 소셜 로그인", description = "Kakao OAuth 인가 코드로 로그인합니다. 신규 유저는 Setup Token을 반환하고 닉네임 설정 단계로 이동합니다.")
    @PostMapping("/login/kakao")
    public ResponseEntity<ApiResponse<SocialLoginResponse>> loginWithKakao(
            @Valid @RequestBody SocialLoginRequest request) {

        OAuthUserInfo userInfo = kakaoOAuthService.getUserInfo(request.code(), request.redirectUri());
        SocialLoginResponse response = authService.socialLogin(AuthProvider.KAKAO, userInfo);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Naver 소셜 로그인 (state 파라미터 필수)
     * POST /api/auth/login/naver
     */
    @Operation(summary = "Naver 소셜 로그인", description = "Naver OAuth 인가 코드로 로그인합니다. state 파라미터가 필수입니다. 신규 유저는 Setup Token 반환.")
    @PostMapping("/login/naver")
    public ResponseEntity<ApiResponse<SocialLoginResponse>> loginWithNaver(
            @Valid @RequestBody SocialLoginRequest request) {

        OAuthUserInfo userInfo = naverOAuthService.getUserInfo(
                request.code(), request.state(), request.redirectUri());
        SocialLoginResponse response = authService.socialLogin(AuthProvider.NAVER, userInfo);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // ══════════════════════════════════════════════════════════════
    //  프로필 설정 - 이메일/소셜 공통 단계
    // ══════════════════════════════════════════════════════════════

    /**
     * STEP 2: 닉네임 설정 → 닉네임이 담긴 새 Setup Token 발급
     * POST /api/auth/signup/profile/nickname
     * Header: Authorization: Bearer {setupToken}
     */
    @Operation(summary = "[Step 2] 닉네임 설정", description = "중복 검사 후 닉네임을 설정하고 갱신된 Setup Token을 반환합니다. Header에 'Authorization: Bearer {setupToken}' 필요.")
    @PostMapping("/signup/profile/nickname")
    public ResponseEntity<ApiResponse<SetupTokenResponse>> setNickname(
            @RequestHeader("Authorization") String authorizationHeader,
            @Valid @RequestBody NicknameSetupRequest request) {

        String setupToken = extractBearerToken(authorizationHeader);
        SetupTokenResponse response = authService.setNickname(setupToken, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * STEP 3: 선호 태그 선택 → 유저 최종 생성 + JWT 발급
     * POST /api/auth/signup/profile/tags
     * Header: Authorization: Bearer {setupToken}
     */
    @Operation(summary = "[Step 3] 선호 태그 선택 → 회원가입 완료", description = "선호 태그(3~5개)를 선택하면 계정이 생성되고 JWT(accessToken + refreshToken)를 반환합니다. Header에 'Authorization: Bearer {setupToken}' 필요.")
    @PostMapping("/signup/profile/tags")
    public ResponseEntity<ApiResponse<LoginResponse>> completeTags(
            @RequestHeader("Authorization") String authorizationHeader,
            @Valid @RequestBody TagSetupRequest request) {

        String setupToken = extractBearerToken(authorizationHeader);
        LoginResponse response = authService.completeSignup(setupToken, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created(response));
    }

    // ══════════════════════════════════════════════════════════════
    //  이메일 로그인 / 토큰 재발급 / 로그아웃 (기존 유지)
    // ══════════════════════════════════════════════════════════════

    @Operation(summary = "이메일 로그인", description = "이메일과 비밀번호로 로그인합니다. 5회 실패 시 계정이 잠깁니다.")
    @PostMapping("/login/email")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request) {

        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "토큰 재발급", description = "Refresh Token으로 새 Access Token + Refresh Token을 발급합니다. (Rotation 방식 — 기존 Refresh Token 무효화)")
    @PostMapping("/reissue")
    public ResponseEntity<ApiResponse<LoginResponse>> reissue(
            @Valid @RequestBody ReissueRequest request) {

        LoginResponse response = authService.reissue(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "로그아웃", description = "현재 Refresh Token을 무효화합니다.")
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout() {
        authService.logout();
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ── Utility ──

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Authorization 헤더가 올바르지 않습니다.");
        }
        return authorizationHeader.substring(7);
    }
}
