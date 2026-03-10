package core.storage;

import core.storage.config.StorageProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "app.storage.s3.provider", havingValue = "aws")
public class AwsS3ObjectStorageService implements ObjectStorageService {
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final StorageProperties props;

    public AwsS3ObjectStorageService(
        S3Client s3Client,
        S3Presigner s3Presigner,
        StorageProperties props
    ) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.props = props;
    }

    @Override
    public PresignedUrlResult generatePutPresignedUrl(String objectKey, String contentType, Duration expiry) {
        try {
            PutObjectRequest objectRequest = PutObjectRequest.builder()
                                                             .bucket(props.bucket())
                                                             .key(objectKey)
                                                             .contentType(contentType)
                                                             .build();

            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                                                                            .signatureDuration(ensureValidExpiry(expiry))
                                                                            .putObjectRequest(objectRequest)
                                                                            .build();

            URL url = s3Presigner.presignPutObject(presignRequest).url();
            return new PresignedUrlResult(objectKey, url, Instant.now().plus(ensureValidExpiry(expiry)));
        } catch (Exception e) {
            throw new StorageException("S3 PUT presigned URL 생성 실패: " + objectKey, e);
        }
    }

    @Override
    public PresignedUrlResult generateGetPresignedUrl(String objectKey, Duration expiry) {
        try {
            GetObjectRequest objectRequest = GetObjectRequest.builder()
                                                             .bucket(props.bucket())
                                                             .key(objectKey)
                                                             .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                                                                            .signatureDuration(ensureValidExpiry(expiry))
                                                                            .getObjectRequest(objectRequest)
                                                                            .build();

            URL url = s3Presigner.presignGetObject(presignRequest).url();
            return new PresignedUrlResult(objectKey, url, Instant.now().plus(ensureValidExpiry(expiry)));
        } catch (Exception e) {
            throw new StorageException("S3 GET presigned URL 생성 실패: " + objectKey, e);
        }
    }

    @Override
    public String buildPublicUrl(String objectKey) {
        // AWS S3의 공식 URL 구조 (CloudFront를 쓴다면 이 부분을 교체하면 됩니다)
        return "https://" + props.bucket() + ".s3." + props.region() + ".amazonaws.com/" + objectKey;
    }

    @Override
    public String buildObjectKey(String prefix, Long contentId, String originalFilename) {
        String safePrefix = trimSlashes(prefix);
        String ext = extractExtension(originalFilename);
        String uuid = UUID.randomUUID().toString();
        return safePrefix + "/" + contentId + "/" + uuid + ext;
    }

    @Override
    public ObjectStat statObject(String objectKey) {
        try {
            HeadObjectResponse res = s3Client.headObject(
                HeadObjectRequest.builder()
                                 .bucket(props.bucket())
                                 .key(objectKey)
                                 .build()
            );

            return new ObjectStat(
                objectKey,
                res.contentLength(),
                res.eTag(),
                res.contentType()
            );
        } catch (NoSuchKeyException e) {
            // MinIO와 동일하게 커스텀 예외로 매핑
            throw new ObjectNotFoundException("UPLOAD_NOT_COMPLETED: S3에 업로드된 파일이 없습니다. PUT 업로드부터 완료하세요.", e);
        } catch (Exception e) {
            throw new StorageException("Object stat 실패(파일 존재/크기 확인): " + objectKey, e);
        }
    }

    @Override
    public void downloadToFile(String objectKey, Path targetFile) {
        try {
            Files.createDirectories(targetFile.getParent());
            GetObjectRequest request = GetObjectRequest.builder()
                                                       .bucket(props.bucket())
                                                       .key(objectKey)
                                                       .build();

            // AWS SDK v2의 편리한 Path 파일 직접 다운로드 기능
            s3Client.getObject(request, targetFile);
        } catch (Exception e) {
            throw new StorageException("S3 다운로드 실패: " + objectKey, e);
        }
    }

    @Override
    public void uploadFromFile(String objectKey, Path sourceFile, String contentType) {
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                                                       .bucket(props.bucket())
                                                       .key(objectKey)
                                                       .contentType(contentType != null ? contentType : "application/octet-stream")
                                                       .build();

            // AWS SDK v2의 편리한 Path 파일 직접 업로드 기능
            s3Client.putObject(request, RequestBody.fromFile(sourceFile));
        } catch (Exception e) {
            throw new StorageException("S3 업로드 실패: " + objectKey, e);
        }
    }

    // --- 헬퍼 메서드들 ---

    private Duration ensureValidExpiry(Duration expiry) {
        if (expiry == null || expiry.getSeconds() <= 0) {
            return Duration.ofMinutes(10);
        }
        long maxSeconds = 60L * 60 * 24 * 7; // 최대 7일
        return expiry.getSeconds() > maxSeconds ? Duration.ofSeconds(maxSeconds) : expiry;
    }

    private String extractExtension(String filename) {
        if (!StringUtils.hasText(filename)) return ".bin";
        String name = filename.trim().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return ".bin";
        return name.substring(dot);
    }

    private String trimSlashes(String s) {
        if (!StringUtils.hasText(s)) return "";
        String r = s;
        while (r.startsWith("/")) r = r.substring(1);
        while (r.endsWith("/")) r = r.substring(0, r.length() - 1);
        return r;
    }
}
