package org.backend.userapi.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 소셜 로그인 응답 DTO
 * <p>
 * - 신규 유저: isNewUser=true, setupToken/setupTokenTtlSeconds 반환
 * - 기존 유저: isNewUser=false, tokenType/accessToken/... 반환
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SocialLoginResponse(
        boolean isNewUser,

        // 신규 유저 전용 필드
        String setupToken,
        Long setupTokenTtlSeconds,

        // 기존 유저 전용 필드
        String tokenType,
        String accessToken,
        Long accessTokenTtlSeconds,
        String refreshToken,
        Long refreshTokenTtlSeconds,
        
        // 회원 상태
        boolean paid,
        boolean uplus
        
) {
    /** 신규 유저 - setup token 반환 */
    public static SocialLoginResponse newUser(String setupToken, long setupTokenTtlSeconds) {
        return new SocialLoginResponse(
                true, setupToken, setupTokenTtlSeconds,
                null, null, null, null, null,
                false, false
        );
    }

    /** 기존 유저 - 정식 JWT 반환 */
    public static SocialLoginResponse existingUser(String tokenType, String accessToken,
                                                   long accessTokenTtlSeconds,
                                                   String refreshToken, long refreshTokenTtlSeconds,                                                   
                                                   boolean paid,
                                                   boolean uplus) {
        return new SocialLoginResponse(
                false, null, null,
                tokenType, accessToken, accessTokenTtlSeconds,
                refreshToken, refreshTokenTtlSeconds,
                paid, uplus
        );
    }
}
