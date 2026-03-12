package core.storage.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {

    @Value("${app.storage.s3.region}")
    private String DEFAULT_REGION;

    @Bean
    @Qualifier("internalMinioClient")
    public MinioClient internalMinioClient(StorageProperties props) {
        return MinioClient.builder()
                .endpoint(props.endpoint())               // http://minio:9000 (컨테이너 내부)
                .credentials(props.accessKey(), props.secretKey())
                .region(DEFAULT_REGION)
                .build();
    }

    @Bean
    @Qualifier("publicMinioClient")
    public MinioClient publicMinioClient(StorageProperties props) {
        return MinioClient.builder()
                .endpoint(props.publicBaseUrl())          // http://localhost:9000 (클라이언트가 호출할 주소)
                .credentials(props.accessKey(), props.secretKey())
                .region(DEFAULT_REGION)
                .build();
    }
}