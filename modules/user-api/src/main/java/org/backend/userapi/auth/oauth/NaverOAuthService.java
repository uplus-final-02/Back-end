package org.backend.userapi.auth.oauth;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

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
        // 네이버는 GET 방식으로 토큰 요청
        String url = UriComponentsBuilder.fromUriString(TOKEN_URL)
                .queryParam("grant_type",    "authorization_code")
                .queryParam("client_id",     clientId)
                .queryParam("client_secret", clientSecret)
                .queryParam("redirect_uri",  redirectUri)
                .queryParam("code",          code)
                .queryParam("state",         state)
                .toUriString();

        NaverTokenResponse response = restTemplate.getForObject(url, NaverTokenResponse.class);

        if (response == null || response.accessToken() == null) {
            throw new IllegalStateException("Naver 액세스 토큰 발급에 실패했습니다.");
        }
        return response.accessToken();
    }

    private OAuthUserInfo fetchUserInfo(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        NaverUserInfoWrapper wrapper = restTemplate.exchange(
                USER_INFO_URL,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                NaverUserInfoWrapper.class
        ).getBody();

        if (wrapper == null || wrapper.response() == null) {
            throw new IllegalStateException("Naver 유저 정보 조회에 실패했습니다.");
        }

        NaverUserInfoWrapper.NaverResponse info = wrapper.response();
        return new OAuthUserInfo(info.id(), info.email(), info.name());
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
