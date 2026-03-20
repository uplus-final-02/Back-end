package core.storage.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.ResourceUtils;
import software.amazon.awssdk.services.cloudfront.CloudFrontUtilities;
import software.amazon.awssdk.services.cloudfront.cookie.CookiesForCustomPolicy;
import software.amazon.awssdk.services.cloudfront.model.CustomSignerRequest;

import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

@Service
@ConditionalOnProperty(name = "app.storage.cloudfront.domain-name")
public class CloudFrontCookieService {

    @Value("${app.storage.cloudfront.domain-name}")
    private String domainName;

    @Value("${app.storage.cloudfront.key-pair-id}")
    private String keyPairId;

    @Value("${app.storage.cloudfront.private-key-string}")
    private String privateKeyString;

    public CookiesForCustomPolicy generateSignedCookies(String resourcePath) throws Exception {
        CloudFrontUtilities cloudFrontUtilities = CloudFrontUtilities.create();

        String resourceUrl = "https://" + domainName + "/" + resourcePath;
        Instant expirationDate = Instant.now().plus(1, ChronoUnit.HOURS);

        // 문자열에서 PrivateKey 객체로 바로 변환
        PrivateKey privateKey = parsePrivateKey(privateKeyString);

        CustomSignerRequest request = CustomSignerRequest.builder()
                                                         .resourceUrl(resourceUrl)
                                                         .privateKey(privateKey) // Path(파일) 대신 PrivateKey 객체를 직접 주입!
                                                         .keyPairId(keyPairId)
                                                         .expirationDate(expirationDate)
                                                         .build();

        return cloudFrontUtilities.getCookiesForCustomPolicy(request);
    }

    // 🌟 PEM 문자열을 Java의 PrivateKey 객체로 변환하는 메서드
    private PrivateKey parsePrivateKey(String pemString) throws Exception {
        // 헤더, 푸터, 줄바꿈 문자 제거하여 순수 Base64 문자열만 추출
        String privateKeyPEM = pemString
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("-----BEGIN RSA PRIVATE KEY-----", "")
            .replace("-----END RSA PRIVATE KEY-----", "")
            .replaceAll("\\s+", ""); // 공백 및 줄바꿈 모두 제거

        byte[] encoded = Base64.getDecoder().decode(privateKeyPEM);

        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(encoded));
    }
}
