package org.backend.userapi.auth.jwt;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import user.entity.User;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.exceptions.JWTVerificationException;

@Component
public class JwtTokenProvider {

    private final Algorithm algorithm;
    private final long accessTokenTtlSeconds;
    private final long refreshTokenTtlSeconds;

    public JwtTokenProvider(
            @Value("${app.jwt.secret}") String jwtSecret,
            @Value("${app.jwt.access-token-ttl-seconds}") long accessTokenTtlSeconds,
            @Value("${app.jwt.refresh-token-ttl-seconds}") long refreshTokenTtlSeconds
    ) {
        this.algorithm = Algorithm.HMAC256(jwtSecret);
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
        this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
    }

    public String generateAccessToken(User user, String email) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(accessTokenTtlSeconds);

        return JWT.create()
                .withSubject(String.valueOf(user.getId()))
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(expiresAt))
                .withClaim("email", email)
                .withClaim("nickname", user.getNickname())
                .withClaim("role", user.getUserRole().name())
                .sign(algorithm);
    }

    public String generateRefreshToken(Long userId) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(refreshTokenTtlSeconds);

        return JWT.create()
                .withSubject(String.valueOf(userId))
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(expiresAt))
                .withClaim("type", "refresh")
                .sign(algorithm);
    }

    public LocalDateTime getRefreshTokenExpiresAt() {
        return LocalDateTime.ofInstant(
                Instant.now().plusSeconds(refreshTokenTtlSeconds),
                ZoneId.systemDefault()
        );
    }

    public long getAccessTokenTtlSeconds() {
        return accessTokenTtlSeconds;
    }

    public long getRefreshTokenTtlSeconds() {
        return refreshTokenTtlSeconds;
    }
    
    public void validateRefreshToken(String token) {
        try {
            DecodedJWT jwt = JWT.require(algorithm).build().verify(token); // 서명/만료 검증
            String type = jwt.getClaim("type").asString();
            if (!"refresh".equals(type)) {
                throw new IllegalArgumentException("리프레시 토큰이 아닙니다.");
            }
        } catch (JWTVerificationException e) {
            throw new IllegalArgumentException("리프레시 토큰이 유효하지 않습니다.");
        }
    }

    public Long extractUserId(String token) {
        try {
            DecodedJWT jwt = JWT.require(algorithm).build().verify(token);
            return Long.parseLong(jwt.getSubject());
        } catch (JWTVerificationException e) {
            throw new IllegalArgumentException("토큰이 유효하지 않습니다.");
        }
    }
}
