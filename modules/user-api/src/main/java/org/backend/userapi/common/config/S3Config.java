package org.backend.userapi.common.config; // 👈 본인 패키지명 확인

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

@Configuration
public class S3Config {

    @Value("${S3_ACCESS_KEY:minioadmin}")
    private String accessKey;

    @Value("${S3_SECRET_KEY:minioadmin123}")
    private String secretKey;

    @Value("${S3_ENDPOINT:http://localhost:9000}")
    private String endpoint;

    @Bean
    public S3Client s3Client() {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);

        return S3Client.builder()
                .credentialsProvider(StaticCredentialsProvider.create(credentials)) 
                .region(Region.of("ap-northeast-2")) 
                .endpointOverride(URI.create(endpoint)) 
                .build();
    }
}