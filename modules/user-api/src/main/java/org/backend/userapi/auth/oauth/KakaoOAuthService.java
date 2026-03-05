package org.backend.userapi.auth.oauth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.backend.userapi.common.exception.OAuthLoginException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.function.Supplier;

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
            log.debug("Kakao OIDC: id_token 방식으로 유저 정보 파싱");
            return parseIdToken(tokenResponse.idToken());
        } else {
            log.debug("Kakao OAuth: /v2/user/me API 방식으로 유저 정보 조회");
            return fetchUserInfo(tokenResponse.accessToken());
        }
    }

    // ── OIDC: id_token 파싱 + 검증 ──

    private OAuthUserInfo parseIdToken(String idToken) {
        String kid = JWT.decode(idToken).getKeyId();
        RSAPublicKey publicKey = fetchPublicKey(kid);

        DecodedJWT verified = JWT.require(Algorithm.RSA256(publicKey, null))
                .withIssuer(ISSUER)
                .withAudience(clientId)
                .build()
                .verify(idToken);

        return new OAuthUserInfo(
                verified.getSubject(),
                verified.getClaim("email").asString(),
                verified.getClaim("nickname").asString()
        );
    }

    private RSAPublicKey fetchPublicKey(String kid) {
        KakaoJwks jwks = callWithRetry(
            () -> restTemplate.getForObject(JWKS_URL, KakaoJwks.class),
            "Kakao"
        );

        if (jwks == null || jwks.keys() == null) {
            throw new OAuthLoginException("Kakao 인증 서버에 연결할 수 없습니다. 잠시 후 다시 시도해주세요.");
        }

        return jwks.keys().stream()
                .filter(k -> kid.equals(k.kid()))
                .findFirst()
                .map(key -> {
                    try {
                        BigInteger n = new BigInteger(1, Base64.getUrlDecoder().decode(key.n()));
                        BigInteger e = new BigInteger(1, Base64.getUrlDecoder().decode(key.e()));
                        RSAPublicKeySpec spec = new RSAPublicKeySpec(n, e);
                        return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(spec);
                    } catch (Exception ex) {
                        throw new OAuthLoginException("Kakao 공개키 파싱에 실패했습니다.", ex);
                    }
                })
                .orElseThrow(() -> new OAuthLoginException("Kakao 인증 처리 중 오류가 발생했습니다. 다시 시도해주세요."));
    }

    // ── Fallback: /v2/user/me API ──

    private OAuthUserInfo fetchUserInfo(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        KakaoUserInfo info = callWithRetry(
            () -> restTemplate.exchange(USER_INFO_URL, HttpMethod.GET, request, KakaoUserInfo.class).getBody(),
            "Kakao"
        );

        if (info == null || info.id() == null) {
            throw new OAuthLoginException("Kakao 유저 정보 조회에 실패했습니다. 다시 시도해주세요.");
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
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        KakaoTokenResponse response = callWithRetry(
            () -> restTemplate.postForObject(TOKEN_URL, request, KakaoTokenResponse.class),
            "Kakao"
        );

        if (response == null || response.accessToken() == null) {
            throw new OAuthLoginException("Kakao 액세스 토큰 발급에 실패했습니다. 다시 시도해주세요.");
        }
        return response;
    }

    // ── 재시도 헬퍼 ──

    private <T> T callWithRetry(Supplier<T> apiCall, String providerName) {
        try {
            return apiCall.get();
        } catch (ResourceAccessException e) {
            log.warn("[{}] 외부 API 응답 없음 - 1회 재시도: {}", providerName, e.getMessage());
            try {
                Thread.sleep(500);
                return apiCall.get();
            } catch (ResourceAccessException retryEx) {
                throw new OAuthLoginException(
                    providerName + " 서버가 응답하지 않습니다. 잠시 후 다시 시도해주세요.", retryEx);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new OAuthLoginException(providerName + " 로그인 처리 중 오류가 발생했습니다.");
            }
        } catch (HttpClientErrorException e) {
            log.warn("[{}] 4xx 응답 - 인가코드 만료 또는 잘못된 요청: {}", providerName, e.getStatusCode());
            throw new OAuthLoginException(
                providerName + " 인증 코드가 만료되었거나 유효하지 않습니다. 다시 로그인해주세요.");
        } catch (HttpServerErrorException e) {
            log.warn("[{}] 5xx 응답 - 제공자 서버 오류: {}", providerName, e.getStatusCode());
            throw new OAuthLoginException(
                providerName + " 서비스가 일시적으로 불안정합니다. 잠시 후 다시 시도해주세요.");
        }
    }

    // ── Internal response records ──

    private record KakaoTokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("id_token")     String idToken
    ) {}

    private record KakaoJwks(List<JwkKey> keys) {
        record JwkKey(
                String kid,
                String kty,
                String alg,
                String use,
                String n,
                String e
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
