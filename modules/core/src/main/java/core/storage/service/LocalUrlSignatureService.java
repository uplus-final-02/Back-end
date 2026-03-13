package core.storage.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;

@Service
public class LocalUrlSignatureService {

    @Value("${app.storage.local-cdn.secret-key:default-secret-key-change-me-in-prod}")
    private String secretKey;
    
    private static final String ALGORITHM = "HmacSHA256";

    /**
     * 지정된 경로와 만료 시간에 대한 서명을 생성합니다.
     * @param path 서명할 URL 경로 (e.g., /api/hls/123/master.m3u8)
     * @param expiry 만료 시간 (Unix timestamp in seconds)
     * @return Base64 URL-safe 인코딩된 서명
     */
    public String sign(String path, long expiry) {
        try {
            String dataToSign = path + "?expires=" + expiry;
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            byte[] signatureBytes = mac.doFinal(dataToSign.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            // 실제 프로덕션 코드에서는 더 정교한 예외 처리가 필요합니다.
            throw new RuntimeException("URL 서명 생성에 실패했습니다.", e);
        }
    }

    /**
     * 주어진 경로, 만료 시간, 서명이 유효한지 검증합니다.
     * @param path 검증할 URL 경로
     * @param expiry 검증할 만료 시간
     * @param signature 검증할 서명
     * @return 유효하면 true, 아니면 false
     */
    public boolean validate(String path, long expiry, String signature) {
        if (Instant.now().getEpochSecond() > expiry) {
            return false; // 토큰 만료
        }
        String expectedSignature = sign(path, expiry);
        return expectedSignature.equals(signature);
    }
}