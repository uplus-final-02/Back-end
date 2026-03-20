package org.backend.userapi.auth.oauth;

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

import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleOAuthService {

    private final RestTemplate restTemplate;

    @Value("${app.oauth2.google.client-id}")
    private String clientId;

    @Value("${app.oauth2.google.client-secret}")
    private String clientSecret;

    private static final String TOKEN_URL    = "https://oauth2.googleapis.com/token";
    private static final String USER_INFO_URL = "https://www.googleapis.com/oauth2/v2/userinfo";

    /**
     * 인가 코드로 Google 유저 정보를 조회합니다.
     *
     * @param code        프론트에서 받은 인가 코드
     * @param redirectUri 프론트에서 사용한 redirect URI
     */
    public OAuthUserInfo getUserInfo(String code, String redirectUri) {
        String accessToken = exchangeCodeForToken(code, redirectUri);
        return fetchUserInfo(accessToken);
    }

    // ── Private helpers ──

    private String exchangeCodeForToken(String code, String redirectUri) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type",    "authorization_code");
        params.add("client_id",     clientId);
        params.add("client_secret", clientSecret);
        params.add("redirect_uri",  redirectUri);
        params.add("code",          code);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        GoogleTokenResponse response = callWithRetry(
            () -> restTemplate.postForObject(TOKEN_URL, request, GoogleTokenResponse.class),
            "Google"
        );

        if (response == null || response.accessToken() == null) {
            throw new OAuthLoginException("Google 액세스 토큰 발급에 실패했습니다. 다시 시도해주세요.");
        }
        return response.accessToken();
    }

    private OAuthUserInfo fetchUserInfo(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        GoogleUserInfo info = callWithRetry(
            () -> restTemplate.exchange(USER_INFO_URL, HttpMethod.GET, request, GoogleUserInfo.class).getBody(),
            "Google"
        );

        if (info == null || info.id() == null) {
            throw new OAuthLoginException("Google 유저 정보 조회에 실패했습니다. 다시 시도해주세요.");
        }
        return new OAuthUserInfo(info.id(), info.email(), info.name());
    }

    // ── 재시도 헬퍼 ──

    /**
     * 외부 API 1회 재시도 헬퍼.
     * ResourceAccessException(타임아웃·네트워크) 발생 시 500ms 후 1회 재시도.
     * 재시도도 실패하면 OAuthLoginException 발생.
     */
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

    private record GoogleTokenResponse(
            @JsonProperty("access_token") String accessToken
    ) {}

    private record GoogleUserInfo(
            String id,
            String email,
            String name
    ) {}
}
