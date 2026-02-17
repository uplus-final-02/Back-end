package org.backend.userapi.auth.dto;

public record LoginResponse(
        String tokenType,
        String accessToken,
        long accessTokenTtlSeconds,
        String refreshToken,
        long refreshTokenTtlSeconds
) {
}
