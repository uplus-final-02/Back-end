package org.backend.userapi.auth.controller;

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

    @PostMapping("/login/email")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request) {

        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/reissue")
    public ResponseEntity<ApiResponse<LoginResponse>> reissue(
            @Valid @RequestBody ReissueRequest request) {

        LoginResponse response = authService.reissue(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

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
