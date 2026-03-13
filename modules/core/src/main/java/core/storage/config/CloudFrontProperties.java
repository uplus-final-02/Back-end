package core.storage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage.cloudfront")
public record CloudFrontProperties(
        String domainName,      // d1234.cloudfront.net
        String keyPairId,       // K1234567890
        String privateKeyPath,  // /path/to/private_key.pem
        boolean useLocal,       // true: MinIO 시뮬레이션, false: 실제 CloudFront
        String signSecret       // 로컬 서명용 비밀키 (임의의 문자열)
) {
}