package org.backend.userapi.auth.oauth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoOAuthService {

    private final RestTemplate restTemplate;

    @Value("${app.oauth2.kakao.client-id}")
    private String clientId;

    @Value("${app.oauth2.kakao.client-secret:}")
    private String clientSecret;

    private static final String TOKEN_URL    = "https://kauth.kakao.com/oauth/token";
    private static final String USER_INFO_URL = "https://kapi.kakao.com/v2/user/me";
    private static final String JWKS_URL     = "https://kauth.kakao.com/.well-known/jwks.json";
    private static final String ISSUER       = "https://kauth.kakao.com";

    /**
     * 카카오 소셜 로그인 유저 정보 조회.
     * id_token(OIDC)이 있으면 파싱, 없으면 /v2/user/me API로 폴백합니다.
     * 프론트에서 scope에 "openid profile email"을 포함해야 id_token이 발급됩니다.
     */
    public OAuthUserInfo getUserInfo(String code, String redirectUri) {
        KakaoTokenResponse tokenResponse = exchangeCodeForTokenResponse(code, redirectUri);

        if (tokenResponse.idToken() != null) {
            // OIDC: id_token 파싱 (API 호출 1번으로 완료)
            log.debug("Kakao OIDC: id_token 방식으로 유저 정보 파싱");
            return parseIdToken(tokenResponse.idToken());
        } else {
            // Fallback: access_token으로 유저 정보 직접 조회
            log.debug("Kakao OAuth: /v2/user/me API 방식으로 유저 정보 조회");
            return fetchUserInfo(tokenResponse.accessToken());
        }
    }

    // ── OIDC: id_token 파싱 + 검증 ──

    /**
     * id_token(JWT)을 카카오 공개키로 검증하고 유저 정보를 추출합니다.
     */
    private OAuthUserInfo parseIdToken(String idToken) {
        // 1. kid 추출 (어떤 공개키로 서명했는지 확인)
        String kid = JWT.decode(idToken).getKeyId();

        // 2. 카카오 JWKS에서 해당 공개키 가져오기
        RSAPublicKey publicKey = fetchPublicKey(kid);

        // 3. 서명 검증 + issuer/audience 확인
        DecodedJWT verified = JWT.require(Algorithm.RSA256(publicKey, null))
                .withIssuer(ISSUER)
                .withAudience(clientId)
                .build()
                .verify(idToken);

        // 4. 클레임 추출
        return new OAuthUserInfo(
                verified.getSubject(),                          // 카카오 고유 ID
                verified.getClaim("email").asString(),          // 이메일 (동의 시)
                verified.getClaim("nickname").asString()        // 닉네임 (동의 시)
        );
    }

    /**
     * 카카오 JWKS 엔드포인트에서 공개키를 가져옵니다.
     */
    private RSAPublicKey fetchPublicKey(String kid) {
        KakaoJwks jwks = restTemplate.getForObject(JWKS_URL, KakaoJwks.class);

        if (jwks == null || jwks.keys() == null) {
            throw new IllegalStateException("Kakao JWKS 조회에 실패했습니다.");
        }

        KakaoJwks.JwkKey key = jwks.keys().stream()
                .filter(k -> kid.equals(k.kid()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Kakao 공개키를 찾을 수 없습니다. kid=" + kid));

        try {
            BigInteger n = new BigInteger(1, Base64.getUrlDecoder().decode(key.n()));
            BigInteger e = new BigInteger(1, Base64.getUrlDecoder().decode(key.e()));
            RSAPublicKeySpec spec = new RSAPublicKeySpec(n, e);
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(spec);
        } catch (Exception ex) {
            throw new IllegalStateException("Kakao 공개키 파싱에 실패했습니다.", ex);
        }
    }

    // ── Fallback: /v2/user/me API ──

    private OAuthUserInfo fetchUserInfo(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        KakaoUserInfo info = restTemplate.exchange(
                USER_INFO_URL,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                KakaoUserInfo.class
        ).getBody();

        if (info == null || info.id() == null) {
            throw new IllegalStateException("Kakao 유저 정보 조회에 실패했습니다.");
        }

        String email    = null;
        String nickname = null;
        if (info.kakaoAccount() != null) {
            email = info.kakaoAccount().email();
            if (info.kakaoAccount().profile() != null) {
                nickname = info.kakaoAccount().profile().nickname();
            }
        }
        return new OAuthUserInfo(String.valueOf(info.id()), email, nickname);
    }

    // ── 토큰 교환 ──

    private KakaoTokenResponse exchangeCodeForTokenResponse(String code, String redirectUri) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type",   "authorization_code");
        params.add("client_id",    clientId);
        params.add("redirect_uri", redirectUri);
        params.add("code",         code);
        if (clientSecret != null && !clientSecret.isBlank()) {
            params.add("client_secret", clientSecret);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        KakaoTokenResponse response = restTemplate.postForObject(
                TOKEN_URL,
                new HttpEntity<>(params, headers),
                KakaoTokenResponse.class
        );

        if (response == null || response.accessToken() == null) {
            throw new IllegalStateException("Kakao 액세스 토큰 발급에 실패했습니다.");
        }
        return response;
    }

    // ── Internal response records ──

    private record KakaoTokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("id_token")     String idToken      // OIDC scope 포함 시 발급
    ) {}

    private record KakaoJwks(List<JwkKey> keys) {
        record JwkKey(
                String kid,   // Key ID
                String kty,   // Key Type (RSA)
                String alg,   // Algorithm (RS256)
                String use,   // Use (sig)
                String n,     // Modulus (Base64URL)
                String e      // Exponent (Base64URL)
        ) {}
    }

    private record KakaoUserInfo(
            Long id,
            @JsonProperty("kakao_account") KakaoAccount kakaoAccount
    ) {
        record KakaoAccount(
                String email,
                KakaoProfile profile
        ) {
            record KakaoProfile(String nickname) {}
        }
    }
}
