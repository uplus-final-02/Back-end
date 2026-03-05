package org.backend.userapi.auth.oauth;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.backend.userapi.common.exception.OAuthLoginException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class NaverOAuthService {

    private final RestTemplate restTemplate;

    @Value("${app.oauth2.naver.client-id}")
    private String clientId;

    @Value("${app.oauth2.naver.client-secret}")
    private String clientSecret;

    private static final String TOKEN_URL     = "https://nid.naver.com/oauth2.0/token";
    private static final String USER_INFO_URL = "https://openapi.naver.com/v1/nid/me";

    /**
     * @param code        프론트에서 받은 인가 코드
     * @param state       CSRF 방지용 state 값 (네이버 필수)
     * @param redirectUri 프론트에서 사용한 redirect URI
     */
    public OAuthUserInfo getUserInfo(String code, String state, String redirectUri) {
        String accessToken = exchangeCodeForToken(code, state, redirectUri);
        return fetchUserInfo(accessToken);
    }

    private String exchangeCodeForToken(String code, String state, String redirectUri) {
        String url = UriComponentsBuilder.fromUriString(TOKEN_URL)
                .queryParam("grant_type",    "authorization_code")
                .queryParam("client_id",     clientId)
                .queryParam("client_secret", clientSecret)
                .queryParam("redirect_uri",  redirectUri)
                .queryParam("code",          code)
                .queryParam("state",         state)
                .toUriString();

        NaverTokenResponse response = callWithRetry(
            () -> restTemplate.getForObject(url, NaverTokenResponse.class),
            "Naver"
        );

        if (response == null || response.accessToken() == null) {
            throw new OAuthLoginException("Naver 액세스 토큰 발급에 실패했습니다. 다시 시도해주세요.");
        }
        return response.accessToken();
    }

    private OAuthUserInfo fetchUserInfo(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        NaverUserInfoWrapper wrapper = callWithRetry(
            () -> restTemplate.exchange(USER_INFO_URL, HttpMethod.GET, request, NaverUserInfoWrapper.class).getBody(),
            "Naver"
        );

        if (wrapper == null || wrapper.response() == null) {
            throw new OAuthLoginException("Naver 유저 정보 조회에 실패했습니다. 다시 시도해주세요.");
        }

        NaverUserInfoWrapper.NaverResponse info = wrapper.response();
        return new OAuthUserInfo(info.id(), info.email(), info.name());
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

    private record NaverTokenResponse(
            @JsonProperty("access_token") String accessToken
    ) {}

    private record NaverUserInfoWrapper(
            String resultcode,
            String message,
            NaverResponse response
    ) {
        record NaverResponse(
                String id,
                String email,
                String name
        ) {}
    }
}
