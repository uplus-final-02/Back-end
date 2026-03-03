package core.storage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage.s3")
public record StorageProperties(
        String provider,        // minio
        String endpoint,        // http://minio:9000 (컨테이너 내부)
        String accessKey,       // minioadmin
        String secretKey,       // minioadmin123
        String bucket,          // app-bucket
        String publicBaseUrl    // http://localhost:9000 (브라우저/로컬)
) {
}