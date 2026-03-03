package org.backend.userapi.common.service;

import java.io.IOException;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3UploadService {

    private final S3Client s3Client;

    @Value("${S3_BUCKET:app-bucket}")
    private String bucket;

    @Value("${S3_ENDPOINT:http://localhost:9000}")
    private String endpoint;

    /**
     * 프로필 이미지를 S3(MinIO)에 업로드하고 URL을 반환합니다.
     */
    public String uploadProfileImage(MultipartFile multipartFile) {
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new IllegalArgumentException("업로드할 이미지가 없습니다.");
        }

        // 1. 파일명 중복 방지를 위한 고유한 파일명 생성 (예: profile/123e4567-e89b..._image.jpg)
        String originalFilename = multipartFile.getOriginalFilename();
        String uniqueFilename = "profile/" + UUID.randomUUID() + "_" + originalFilename;

        try {
            // 2. S3(MinIO) 업로드 요청 객체 조립
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(uniqueFilename)
                    .contentType(multipartFile.getContentType())
                    .build();

            // 3. 파일 업로드 실행
            s3Client.putObject(putObjectRequest,
                    RequestBody.fromInputStream(multipartFile.getInputStream(), multipartFile.getSize()));

            // 4. 업로드된 파일에 접근할 수 있는 URL 조합 후 반환
            // 형식: http://localhost:9000/app-bucket/profile/파일명.jpg
            return endpoint + "/" + bucket + "/" + uniqueFilename;

        } catch (IOException e) {
            log.error("프로필 이미지 업로드 실패: {}", e.getMessage());
            throw new RuntimeException("프로필 이미지 업로드 중 오류가 발생했습니다.");
        }
    }
}