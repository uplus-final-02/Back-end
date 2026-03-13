package core.storage.service;

import core.storage.config.CloudFrontProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cloudfront.CloudFrontUtilities;
import software.amazon.awssdk.services.cloudfront.model.CannedSignerRequest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class CloudFrontService {

    private final CloudFrontProperties properties;
    private final CloudFrontUtilities cloudFrontUtilities = CloudFrontUtilities.create();

    /**
     * CloudFront Signed URL 생성 (또는 로컬 시뮬레이션 URL)
     * @param objectKey S3 객체 키 (예: hls/1/master.m3u8)
     * @return 서명된 전체 URL
     */
    public String generateSignedUrl(String objectKey) {
        if (properties.useLocal()) {
            return generateLocalSignedUrl(objectKey);
        } else {
            return generateCloudFrontSignedUrl(objectKey);
        }
    }

    private String generateCloudFrontSignedUrl(String objectKey) {
        try {
            String resourceUrl = "https://" + properties.domainName() + "/" + objectKey;
            Path privateKeyPath = Paths.get(properties.privateKeyPath());
            String keyPairId = properties.keyPairId();
            Instant expirationDate = Instant.now().plus(1, ChronoUnit.HOURS);

            CannedSignerRequest cannedRequest = CannedSignerRequest.builder()
                    .resourceUrl(resourceUrl)
                    .privateKey(privateKeyPath)
                    .keyPairId(keyPairId)
                    .expirationDate(expirationDate)
                    .build();

            return cloudFrontUtilities.getSignedUrlWithCannedPolicy(cannedRequest).url();
        } catch (Exception e) {
            log.error("Failed to generate CloudFront signed URL for key: {}", objectKey, e);
            return null;
        }
    }

    // 로컬 시뮬레이션: /api/hls/{videoFileId}/master.m3u8?expires=...&signature=...
    private String generateLocalSignedUrl(String objectKey) {
        try {
            // objectKey: hls/{videoFileId}/master.m3u8
            // 추출: videoFileId
            String[] parts = objectKey.split("/");
            if (parts.length < 3) return null;
            String videoFileId = parts[1];

            long expires = Instant.now().plus(1, ChronoUnit.HOURS).getEpochSecond();
            String signature = createLocalSignature(videoFileId, expires);

            return String.format("/api/hls/%s/master.m3u8?expires=%d&signature=%s",
                    videoFileId, expires, signature);
        } catch (Exception e) {
            log.error("Failed to generate local signed URL", e);
            return null;
        }
    }

    public boolean verifyLocalSignature(String videoFileId, long expires, String signature) {
        if (!properties.useLocal()) return true; // 로컬 모드가 아니면 검증 패스 (또는 로직 분리)
        
        if (Instant.now().getEpochSecond() > expires) {
            log.warn("Local signed URL expired");
            return false;
        }

        String expected = createLocalSignature(videoFileId, expires);
        return expected.equals(signature);
    }

    private String createLocalSignature(String videoFileId, long expires) {
        try {
            String data = videoFileId + ":" + expires;
            Mac hmacSha256 = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(properties.signSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            hmacSha256.init(secretKey);
            byte[] hash = hmacSha256.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create local signature", e);
        }
    }
}