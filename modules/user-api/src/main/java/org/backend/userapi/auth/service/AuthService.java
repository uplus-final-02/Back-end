package org.backend.userapi.auth.service;

import com.auth0.jwt.interfaces.DecodedJWT;
import common.entity.Tag;
import common.enums.AuthProvider;
import common.enums.UserStatus;
import common.repository.TagRepository;
import core.security.exception.JwtInvalidTokenException;
import core.security.exception.JwtTokenExpiredException;
import core.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.backend.userapi.auth.dto.*;
import core.security.principal.JwtPrincipal;
import org.backend.userapi.auth.oauth.OAuthUserInfo;
import org.backend.userapi.common.exception.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import user.entity.AuthAccount;
import user.entity.RefreshToken;
import user.entity.User;
import user.entity.UserPreferredTag;
import user.repository.AuthAccountRepository;
import user.repository.UserPreferredTagRepository;
import user.repository.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthAccountRepository authAccountRepository;
    private final UserRepository userRepository;
    private final UserPreferredTagRepository userPreferredTagRepository;
    private final TagRepository tagRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final EmailVerificationService emailVerificationService;

    // ══════════════════════════════════════════════════════════════
    //  STEP 1: 이메일 인증코드 발송
    // ══════════════════════════════════════════════════════════════

    /**
     * 이메일 중복/소셜 충돌 검사 후 인증코드 발송.
     */
    public void sendEmailVerificationCode(String email) {
        validateEmailConflicts(email);
        emailVerificationService.sendCode(email);
    }

    // ══════════════════════════════════════════════════════════════
    //  STEP 2: 이메일 인증코드 검증 → Setup Token 발급
    // ══════════════════════════════════════════════════════════════

    /**
     * 인증코드 검증 성공 시 Setup Token 발급.
     * 비밀번호 해시를 토큰에 담아 DB 저장 없이 다음 단계로 전달합니다.
     */
    public SetupTokenResponse verifyEmailCode(EmailVerifyCodeRequest request) {
        emailVerificationService.verifyCode(request.email(), request.code());

        String passwordHash = passwordEncoder.encode(request.password());
        String setupToken = jwtTokenProvider.generateSetupToken(
                AuthProvider.EMAIL.name(), request.email(), passwordHash, null, null);

        return new SetupTokenResponse(setupToken, jwtTokenProvider.getSetupTokenTtlSeconds());
    }

    // ══════════════════════════════════════════════════════════════
    //  STEP 3: 닉네임 설정 → Setup Token 갱신 (이메일/소셜 공통)
    // ══════════════════════════════════════════════════════════════

    /**
     * 닉네임 중복 검사 후, 닉네임이 추가된 새 Setup Token을 발급합니다.
     */
    public SetupTokenResponse setNickname(String setupToken, NicknameSetupRequest request) {
        DecodedJWT decoded = extractSetupToken(setupToken);

        validateDuplicateNickname(request.nickname());

        String newSetupToken = jwtTokenProvider.generateSetupToken(
                decoded.getClaim("provider").asString(),
                decoded.getClaim("email").asString(),
                decoded.getClaim("passwordHash").asString(),
                decoded.getClaim("providerSubject").asString(),
                request.nickname()
        );

        return new SetupTokenResponse(newSetupToken, jwtTokenProvider.getSetupTokenTtlSeconds());
    }

    // ══════════════════════════════════════════════════════════════
    //  STEP 4: 태그 선택 → 유저 최종 생성 + JWT 발급 (이메일/소셜 공통)
    // ══════════════════════════════════════════════════════════════

    /**
     * Setup Token의 정보로 유저를 최종 생성하고 JWT를 발급합니다.
     * 이메일 가입과 소셜 가입 모두 이 엔드포인트를 공유합니다.
     */
    @Transactional
    public LoginResponse completeSignup(String setupToken, TagSetupRequest request) {
        DecodedJWT decoded = extractSetupToken(setupToken);

        // 닉네임 단계 완료 여부 확인
        String nickname = decoded.getClaim("nickname").asString();
        if (nickname == null || nickname.isBlank()) {
            throw new InvalidSetupTokenException("닉네임 설정 단계를 먼저 완료해주세요.");
        }

        String provider        = decoded.getClaim("provider").asString();
        String email           = decoded.getClaim("email").asString();
        String passwordHash    = decoded.getClaim("passwordHash").asString();
        String providerSubject = decoded.getClaim("providerSubject").asString();

        // 태그 검증
        validateTagCount(request.tagIds());
        List<Tag> tags = validateAndGetTags(request.tagIds());

        // User 생성
        User user = User.builder().nickname(nickname).build();
        userRepository.save(user);

        // AuthAccount 생성 (provider에 따라 분기)
        AuthProvider authProvider = AuthProvider.valueOf(provider);
        AuthAccount authAccount;
        if (authProvider == AuthProvider.EMAIL) {
            authAccount = AuthAccount.builder()
                    .user(user)
                    .authProvider(AuthProvider.EMAIL)
                    .authProviderSubject(email)
                    .email(email)
                    .passwordHash(passwordHash)
                    .build();
        } else {
            authAccount = AuthAccount.builder()
                    .user(user)
                    .authProvider(authProvider)
                    .authProviderSubject(providerSubject)
                    .email(email)
                    .build();
        }
        authAccountRepository.save(authAccount);

        // UserPreferredTag 저장
        List<UserPreferredTag> preferredTags = tags.stream()
                .map(tag -> UserPreferredTag.builder().user(user).tag(tag).build())
                .toList();
        userPreferredTagRepository.saveAll(preferredTags);

        return issueJwt(user, email);
    }

    // ══════════════════════════════════════════════════════════════
    //  소셜 로그인 (Google / Kakao / Naver 공통)
    // ══════════════════════════════════════════════════════════════

    /**
     * OAuth 유저 정보로 로그인 또는 신규 가입 플로우를 처리합니다.
     *
     * @param authProvider 소셜 제공자 (GOOGLE, KAKAO, NAVER)
     * @param userInfo     OAuth에서 받은 유저 정보
     * @return 기존 유저: JWT 포함 응답 / 신규 유저: Setup Token 포함 응답
     */
    @Transactional
    public SocialLoginResponse socialLogin(AuthProvider authProvider, OAuthUserInfo userInfo) {
        return authAccountRepository
                .findByAuthProviderAndAuthProviderSubject(authProvider, userInfo.providerSubject())
                .map(existing -> {
                    // 기존 유저 → 바로 로그인
                    existing.updateLastLoginAt();
                    User user = existing.getUser();

                    if (user.getUserStatus() != UserStatus.ACTIVE) {
                        throw new InvalidCredentialsException("비활성화된 계정입니다.");
                    }

                    LoginResponse jwt = issueJwt(user, existing.getEmail());
                    return SocialLoginResponse.existingUser(
                            jwt.tokenType(), jwt.accessToken(), jwt.accessTokenTtlSeconds(),
                            jwt.refreshToken(), jwt.refreshTokenTtlSeconds()
                    );
                })
                .orElseGet(() -> {
                    // 신규 유저 → EMAIL 계정과의 충돌 확인 후 Setup Token 발급
                    if (userInfo.email() != null) {
                        validateSocialEmailConflict(userInfo.email());
                    }

                    String setupToken = jwtTokenProvider.generateSetupToken(
                            authProvider.name(),
                            userInfo.email(),
                            null,
                            userInfo.providerSubject(),
                            null
                    );
                    return SocialLoginResponse.newUser(setupToken, jwtTokenProvider.getSetupTokenTtlSeconds());
                });
    }

    // ══════════════════════════════════════════════════════════════
    //  이메일 로그인 (기존과 동일)
    // ══════════════════════════════════════════════════════════════

    @Transactional
    public LoginResponse login(LoginRequest request) {
        AuthAccount authAccount = authAccountRepository
                .findByAuthProviderAndEmail(AuthProvider.EMAIL, request.email())
                .orElseThrow(() -> new InvalidCredentialsException("이메일 또는 비밀번호가 올바르지 않습니다."));

        if (authAccount.getPasswordHash() == null
                || !passwordEncoder.matches(request.password(), authAccount.getPasswordHash())) {
            throw new InvalidCredentialsException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        User user = authAccount.getUser();
        if (user.getUserStatus() != UserStatus.ACTIVE) {
            throw new InvalidCredentialsException("비활성화된 계정입니다.");
        }

        authAccount.updateLastLoginAt();
        return issueJwt(user, authAccount.getEmail());
    }

    // ══════════════════════════════════════════════════════════════
    //  토큰 재발급 / 로그아웃 (기존과 동일)
    // ══════════════════════════════════════════════════════════════

    @Transactional
    public LoginResponse reissue(ReissueRequest request) {
        Long userId = jwtTokenProvider.extractUserIdFromRefreshToken(request.refreshToken());

        RefreshToken saved = refreshTokenService.validateStoredTokenAndGet(userId, request.refreshToken());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidCredentialsException("사용자 정보를 찾을 수 없습니다."));

        if (user.getUserStatus() != UserStatus.ACTIVE) {
            throw new InvalidCredentialsException("비활성화된 계정입니다.");
        }

        AuthAccount authAccount = authAccountRepository.findByUser_Id(userId)
                .orElseThrow(() -> new InvalidCredentialsException("인증 정보를 찾을 수 없습니다."));

        String newAccessToken  = jwtTokenProvider.generateAccessToken(
                user.getId(), authAccount.getEmail(), user.getNickname(), user.getUserRole().name());
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(userId);

        refreshTokenService.upsert(userId, newRefreshToken);

        return new LoginResponse(
                "Bearer", newAccessToken, jwtTokenProvider.getAccessTokenTtlSeconds(),
                newRefreshToken, jwtTokenProvider.getRefreshTokenTtlSeconds()
        );
    }

    @Transactional
    public void logout() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            throw new InvalidCredentialsException("인증 정보가 없습니다.");
        }
        JwtPrincipal principal = (JwtPrincipal) auth.getPrincipal();
        refreshTokenService.deleteByUserId(principal.getUserId());
    }

    // ══════════════════════════════════════════════════════════════
    //  Private helpers
    // ══════════════════════════════════════════════════════════════

    /** Setup Token 검증 및 디코딩. 만료/위변조 시 InvalidSetupTokenException으로 변환. */
    private DecodedJWT extractSetupToken(String rawToken) {
        try {
            return jwtTokenProvider.validateAndGetSetupToken(rawToken);
        } catch (JwtTokenExpiredException e) {
            throw new InvalidSetupTokenException("가입 세션이 만료되었습니다. 처음부터 다시 시작해주세요.");
        } catch (JwtInvalidTokenException e) {
            throw new InvalidSetupTokenException("유효하지 않은 가입 토큰입니다.");
        }
    }

    /** JWT 발급 + Refresh Token DB 저장 */
    private LoginResponse issueJwt(User user, String email) {
        String accessToken  = jwtTokenProvider.generateAccessToken(
                user.getId(), email, user.getNickname(), user.getUserRole().name());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());
        refreshTokenService.upsert(user.getId(), refreshToken);

        return new LoginResponse(
                "Bearer", accessToken, jwtTokenProvider.getAccessTokenTtlSeconds(),
                refreshToken, jwtTokenProvider.getRefreshTokenTtlSeconds()
        );
    }

    /** 이메일 가입 시: EMAIL 중복 + 소셜 충돌 통합 검사 */
    private void validateEmailConflicts(String email) {
        List<AuthAccount> existing = authAccountRepository.findAllByEmail(email);
        for (AuthAccount acc : existing) {
            if (acc.getAuthProvider() == AuthProvider.EMAIL) {
                throw new DuplicateEmailException("이미 이메일로 가입된 계정입니다.");
            } else {
                throw new SocialProviderConflictException(
                        "해당 이메일은 " + acc.getAuthProvider().getDescription()
                        + " 계정으로 이미 가입되어 있습니다. "
                        + acc.getAuthProvider().getDescription() + " 로그인을 이용해주세요.");
            }
        }
    }

    /** 소셜 가입 시: EMAIL 제공자와의 충돌만 검사 */
    private void validateSocialEmailConflict(String email) {
        authAccountRepository.findByAuthProviderAndEmail(AuthProvider.EMAIL, email)
                .ifPresent(acc -> {
                    throw new SocialProviderConflictException(
                            "해당 이메일은 이미 이메일 계정으로 가입되어 있습니다. 이메일 로그인을 이용해주세요.");
                });
    }

    private void validateDuplicateNickname(String nickname) {
        if (userRepository.existsByNickname(nickname)) {
            throw new DuplicateNicknameException("이미 사용 중인 닉네임입니다.");
        }
    }

    private void validateTagCount(List<Long> tagIds) {
        if (tagIds.size() < 3 || tagIds.size() > 5) {
            throw new InvalidTagException("선호 태그는 3개 이상 5개 이하로 선택해야 합니다.");
        }
    }

    private List<Tag> validateAndGetTags(List<Long> tagIds) {
        List<Tag> tags = tagRepository.findAllByIdIn(tagIds);
        if (tags.size() != tagIds.size()) {
            throw new InvalidTagException("유효하지 않은 태그 ID가 포함되어 있습니다.");
        }
        return tags;
    }
}
