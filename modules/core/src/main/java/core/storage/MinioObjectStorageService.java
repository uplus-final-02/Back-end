package core.storage;

import core.storage.config.StorageProperties;
import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@ConditionalOnProperty(name = "app.storage.s3.provider", havingValue = "minio", matchIfMissing = true)
public class MinioObjectStorageService implements ObjectStorageService {

    private final MinioClient internalMinioClient; // (필요 시) 서버 내부용
    private final MinioClient publicMinioClient;   // presigned 발급용(= localhost:9000 기준)
    private final StorageProperties props;

    public MinioObjectStorageService(
            @Qualifier("internalMinioClient") MinioClient internalMinioClient,
            @Qualifier("publicMinioClient") MinioClient publicMinioClient,
            StorageProperties props
    ) {
        this.internalMinioClient = internalMinioClient;
        this.publicMinioClient = publicMinioClient;
        this.props = props;
    }

    @Override
    public PresignedUrlResult generatePutPresignedUrl(String objectKey, String contentType, Duration expiry) {
        try {
            int seconds = toSeconds(expiry);

            String presigned = publicMinioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(props.bucket())
                            .object(objectKey)
                            .expiry(seconds)
                            .build()
            );

            URL url = new URL(presigned);
            return new PresignedUrlResult(objectKey, url, Instant.now().plusSeconds(seconds));
        } catch (Exception e) {
            throw new StorageException("PUT presigned URL 생성 실패: " + objectKey, e);
        }
    }

    @Override
    public PresignedUrlResult generateGetPresignedUrl(String objectKey, Duration expiry) {
        try {
            int seconds = toSeconds(expiry);

            String presigned = publicMinioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(props.bucket())
                            .object(objectKey)
                            .expiry(seconds)
                            .build()
            );

            URL url = new URL(presigned);
            return new PresignedUrlResult(objectKey, url, Instant.now().plusSeconds(seconds));
        } catch (Exception e) {
            throw new StorageException("GET presigned URL 생성 실패: " + objectKey, e);
        }
    }

    @Override
    public String buildPublicUrl(String objectKey) {
        String base = trimTrailingSlash(props.publicBaseUrl());
        return base + "/" + props.bucket() + "/" + objectKey;
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
            StatObjectResponse res = internalMinioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(props.bucket())
                            .object(objectKey)
                            .build()
            );

            return new ObjectStat(
                    objectKey,
                    res.size(),
                    res.etag(),
                    res.contentType()
            );
        } catch (ErrorResponseException e) {
            String code = (e.errorResponse() != null) ? e.errorResponse().code() : null;
            if ("NoSuchKey".equalsIgnoreCase(code) || "NoSuchObject".equalsIgnoreCase(code)) {
                throw new ObjectNotFoundException("UPLOAD_NOT_COMPLETED: MinIO에 업로드된 파일이 없습니다. PUT 업로드부터 완료하세요.", e);
            }
            throw new StorageException("Object stat 실패(파일 존재/크기 확인): " + objectKey, e);
        } catch (Exception e) {
            throw new StorageException("Object stat 실패(파일 존재/크기 확인): " + objectKey, e);
        }
    }

    private int toSeconds(Duration expiry) {
        if (expiry == null) return 60 * 10;
        long seconds = expiry.getSeconds();
        if (seconds <= 0) return 60 * 10;
        return (int) Math.min(seconds, 60L * 60 * 24 * 7);
    }

    private String extractExtension(String filename) {
        if (!StringUtils.hasText(filename)) return ".bin";
        String name = filename.trim().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return ".bin";
        return name.substring(dot);
    }

    private String trimTrailingSlash(String s) {
        if (!StringUtils.hasText(s)) return "";
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private String trimSlashes(String s) {
        if (!StringUtils.hasText(s)) return "";
        String r = s;
        while (r.startsWith("/")) r = r.substring(1);
        while (r.endsWith("/")) r = r.substring(0, r.length() - 1);
        return r;
    }

    @Override
    public void downloadToFile(String objectKey, Path targetFile) {
        try (InputStream in = internalMinioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(props.bucket())
                        .object(objectKey)
                        .build()
        )) {
            Files.createDirectories(targetFile.getParent());
            Files.copy(in, targetFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            throw new StorageException("MinIO 다운로드 실패: " + objectKey, e);
        }
    }

    @Override
    public void uploadFromFile(String objectKey, Path sourceFile, String contentType) {
        try (InputStream in = Files.newInputStream(sourceFile)) {
            long size = Files.size(sourceFile);

            internalMinioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(props.bucket())
                            .object(objectKey)
                            .stream(in, size, -1)
                            .contentType(contentType != null ? contentType : "application/octet-stream")
                            .build()
            );
        } catch (Exception e) {
            throw new StorageException("MinIO 업로드 실패: " + objectKey, e);
        }
    }
}