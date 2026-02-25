package org.backend.userapi.auth.oauth;

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

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleOAuthService {

    private final RestTemplate restTemplate;

    @Value("${app.oauth2.google.client-id}")
    private String clientId;

    @Value("${app.oauth2.google.client-secret}")
    private String clientSecret;

    private static final String TOKEN_URL  = "https://oauth2.googleapis.com/token";
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

        GoogleTokenResponse response = restTemplate.postForObject(
                TOKEN_URL,
                new HttpEntity<>(params, headers),
                GoogleTokenResponse.class
        );

        if (response == null || response.accessToken() == null) {
            throw new IllegalStateException("Google 액세스 토큰 발급에 실패했습니다.");
        }
        return response.accessToken();
    }

    private OAuthUserInfo fetchUserInfo(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        GoogleUserInfo info = restTemplate.exchange(
                USER_INFO_URL,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                GoogleUserInfo.class
        ).getBody();

        if (info == null || info.id() == null) {
            throw new IllegalStateException("Google 유저 정보 조회에 실패했습니다.");
        }
        return new OAuthUserInfo(info.id(), info.email(), info.name());
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
