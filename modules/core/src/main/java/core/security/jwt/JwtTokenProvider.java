package core.security.jwt;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.exceptions.JWTVerificationException;
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
    private final long setupTokenTtlSeconds;

    public JwtTokenProvider(
            @Value("${app.jwt.secret}") String jwtSecret,
            @Value("${app.jwt.access-token-ttl-seconds}") long accessTokenTtlSeconds,
            @Value("${app.jwt.refresh-token-ttl-seconds}") long refreshTokenTtlSeconds,
            @Value("${app.jwt.setup-token-ttl-seconds:600}") long setupTokenTtlSeconds
    ) {
        this.algorithm = Algorithm.HMAC256(jwtSecret);
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
        this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
        this.setupTokenTtlSeconds = setupTokenTtlSeconds;
    }

    // ── Access / Refresh Token ──

    public String generateAccessToken(Long userId, String email, String nickname, String role) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(accessTokenTtlSeconds);

        return JWT.create()
                .withSubject(String.valueOf(userId))
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(expiresAt))
                .withClaim("email", email)
                .withClaim("nickname", nickname)
                .withClaim("role", role)
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

    // ── Setup Token ──

    /**
     * 회원가입 다단계 플로우에서 사용하는 임시 토큰.
     * DB에 유저를 저장하기 전, 인증된 정보를 단계 간에 전달합니다.
     *
     * @param provider        AuthProvider 이름 (EMAIL, GOOGLE, KAKAO, NAVER)
     * @param email           이메일 (소셜의 경우 null 가능)
     * @param passwordHash    BCrypt 해시 (EMAIL만 사용, 소셜은 null)
     * @param providerSubject OAuth 제공자의 고유 ID (소셜만 사용, EMAIL은 null)
     * @param nickname        닉네임 단계 완료 후 추가 (미완료 시 null)
     */
    public String generateSetupToken(String provider, String email,
                                     String passwordHash, String providerSubject,
                                     String nickname) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(setupTokenTtlSeconds);

        JWTCreator.Builder builder = JWT.create()
                .withSubject("setup")
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(expiresAt))
                .withClaim("type", "setup")
                .withClaim("provider", provider);

        if (email != null)           builder.withClaim("email", email);
        if (passwordHash != null)    builder.withClaim("passwordHash", passwordHash);
        if (providerSubject != null) builder.withClaim("providerSubject", providerSubject);
        if (nickname != null)        builder.withClaim("nickname", nickname);

        return builder.sign(algorithm);
    }

    /**
     * Setup Token 유효성 검증 후 DecodedJWT 반환.
     * type 클레임이 "setup"인지 검사합니다.
     */
    public DecodedJWT validateAndGetSetupToken(String token) {
        return validateAndGet(token, "setup");
    }

    public long getSetupTokenTtlSeconds() {
        return setupTokenTtlSeconds;
    }

    // ── TTL helpers ──

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

    // ── Validation ──

    public DecodedJWT validateAndGet(String token, String expectedType) {
        try {
            DecodedJWT jwt = JWT.require(algorithm).build().verify(token);

            if (expectedType != null) {
                String type = jwt.getClaim("type").asString();
                if (!expectedType.equals(type)) {
                    throw new JwtInvalidTokenException("토큰 타입이 올바르지 않습니다.");
                }
            }
            return jwt;

        } catch (com.auth0.jwt.exceptions.TokenExpiredException e) {
            throw new JwtTokenExpiredException("토큰이 만료되었습니다.");
        } catch (JWTVerificationException e) {
            throw new JwtInvalidTokenException("토큰이 유효하지 않습니다.");
        }
    }

    /**
     * Access Token에서 userId 추출.
     * type 클레임이 있는 토큰(refresh, setup)은 거부합니다.
     */
    public Long extractUserId(String token) {
        DecodedJWT jwt = validateAndGet(token, null);
        // Access Token은 type 클레임이 없음 → type이 있으면 잘못된 토큰
        String type = jwt.getClaim("type").asString();
        if (type != null) {
            throw new JwtInvalidTokenException("토큰이 유효하지 않습니다.");
        }
        return Long.parseLong(jwt.getSubject());
    }

    public Long extractUserIdFromRefreshToken(String refreshToken) {
        DecodedJWT jwt = validateAndGet(refreshToken, "refresh");
        return Long.parseLong(jwt.getSubject());
    }
    
    public String generateAccessToken(Long userId, String email, String nickname, String role,
            boolean paid, boolean uplus) {
    	
		Instant now = Instant.now();
		Instant expiresAt = now.plusSeconds(accessTokenTtlSeconds);
		
		return JWT.create()
		.withSubject(String.valueOf(userId))
		.withIssuedAt(Date.from(now))
		.withExpiresAt(Date.from(expiresAt))
		.withClaim("email", email)
		.withClaim("nickname", nickname)
		.withClaim("role", role)
		.withClaim("paid", paid)
		.withClaim("uplus", uplus)
		.sign(algorithm);
	}
}
