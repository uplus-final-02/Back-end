package core.security.jwt;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.exceptions.TokenExpiredException;
import core.security.exception.JwtInvalidTokenException;
import core.security.exception.JwtTokenExpiredException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;


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

    public String generateAccessToken(Long userId, String email, String nickname, String role) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(accessTokenTtlSeconds);

        return JWT.create()
                .withSubject(String.valueOf(userId))
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(expiresAt))
                .withClaim("email", email)
                .withClaim("nickname", nickname)
                .withClaim("role", role) // "USER" / "ADMIN"
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

    
    public DecodedJWT validateAndGet(String token, String expectedType) {
        try {
            DecodedJWT jwt = JWT.require(algorithm).build().verify(token); // 서명/만료 검증
            
            if (expectedType != null) {
                String type = jwt.getClaim("type").asString();
                if (!expectedType.equals(type)) {
                	throw new JwtInvalidTokenException("토큰 타입이 올바르지 않습니다.");
                }
            }
            return jwt;
            
        } catch (com.auth0.jwt.exceptions.TokenExpiredException e) { //Auth0 예외를 잡기 위해.
            throw new JwtTokenExpiredException("토큰이 만료되었습니다.");
        } catch (JWTVerificationException e) {
            throw new JwtInvalidTokenException("토큰이 유효하지 않습니다.");
        }
    }

    public Long extractUserId(String token) {
        DecodedJWT jwt = validateAndGet(token, null);
        return Long.parseLong(jwt.getSubject());
    }

    public Long extractUserIdFromRefreshToken(String refreshToken) {
        DecodedJWT jwt = validateAndGet(refreshToken, "refresh");
        return Long.parseLong(jwt.getSubject());
    }

   
}
