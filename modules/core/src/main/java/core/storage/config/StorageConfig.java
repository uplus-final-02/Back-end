package core.storage.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {

//    @Value("${app.storage.s3.region}")
//    private String DEFAULT_REGION;
//
//    @Bean
//    @Qualifier("internalMinioClient")
//    public MinioClient internalMinioClient(StorageProperties props) {
//        return MinioClient.builder()
//                .endpoint(props.endpoint())               // http://minio:9000 (컨테이너 내부)
//                .credentials(props.accessKey(), props.secretKey())
//                .region(DEFAULT_REGION)
//                .build();
//    }
//
//    @Bean
//    @Qualifier("publicMinioClient")
//    public MinioClient publicMinioClient(StorageProperties props) {
//        return MinioClient.builder()
//                .endpoint(props.publicBaseUrl())          // http://localhost:9000 (클라이언트가 호출할 주소)
//                .credentials(props.accessKey(), props.secretKey())
//                .region(DEFAULT_REGION)
//                .build();
//    }

    @Bean
    public S3Client s3Client(StorageProperties props) {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
            props.accessKey(),
            props.secretKey()
        );

        return S3Client.builder()
                       .region(Region.of(props.region()))
                       .credentialsProvider(StaticCredentialsProvider.create(credentials))
                       .build();
    }

    @Bean
    public S3Presigner s3Presigner(StorageProperties props) {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(props.accessKey(), props.secretKey());
        return S3Presigner.builder()
                          .region(Region.of(props.region()))
                          .credentialsProvider(StaticCredentialsProvider.create(credentials))
                          .build();
    }
}