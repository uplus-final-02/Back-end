package org.backend.userapi.hls;

import core.storage.config.StorageProperties;
import core.storage.service.LocalUrlSignatureService;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.errors.MinioException;
import io.minio.MinioClient;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/hls")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.storage.s3.provider", havingValue = "minio", matchIfMissing = true)
public class UserHlsProxyController {

    @Qualifier("internalMinioClient")
    private final MinioClient internalMinioClient;

    @Qualifier("publicMinioClient")
    private final MinioClient publicMinioClient;

    private final StorageProperties props;
    private final LocalUrlSignatureService urlSignatureService;

    // 사용자용 HLS 프록시: master.m3u8을 읽어서 하위 m3u8(variant)에 대한 접근 URL을 서명하여 반환
    @GetMapping(value = "/{videoFileId}/master.m3u8", produces = "application/vnd.apple.mpegurl")
    public String getRewrittenMaster(
            @PathVariable Long videoFileId,
            @RequestParam(name = "expires") Long expires,
            @RequestParam(name = "signature") String signature
    ) {
        // 0) 서명 검증
        String path = "/api/hls/" + videoFileId + "/master.m3u8";
        if (!urlSignatureService.validate(path, expires, signature)) {
            log.warn("Invalid HLS signature. path={}, expires={}, signature={}", path, expires, signature);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid or expired signature");
        }
        
        String masterKey = "hls/" + videoFileId + "/master.m3u8";
        String raw = loadFromMinio(masterKey);

        // 2) URL Rewrite
        return raw.lines()
                .map(line -> {
                    String trimmed = line.trim();
                    if (trimmed.isBlank() || trimmed.startsWith("#")) return trimmed;

                    // 하위 m3u8 (variant playlist) 인 경우
                    if (trimmed.endsWith(".m3u8")) {
                        // 예: v0/prog_index.m3u8 -> /api/hls/{id}/v0/prog_index.m3u8
                        // 주의: trimmed 자체가 v0/prog_index.m3u8 같은 상대 경로라고 가정
                        String variantPath = "/api/hls/" + videoFileId + "/" + trimmed;
                        String newSig = urlSignatureService.sign(variantPath, expires);
                        return variantPath + "?expires=" + expires + "&signature=" + newSig;
                    }

                    // (구버전 호환 또는 단일 파일) 혹시 master에 바로 ts가 있는 경우
                    if (trimmed.endsWith(".ts")) {
                         String segKey = "hls/" + videoFileId + "/" + trimmed;
                         return generatePresignedUrl(segKey);
                    }

                    return trimmed;
                })
                .collect(Collectors.joining("\n")) + "\n";
    }

    // 화질별 m3u8 프록시: prog_index.m3u8을 읽어서 ts 파일을 Presigned URL로 변환
    @GetMapping(value = "/{videoFileId}/{quality}/prog_index.m3u8", produces = "application/vnd.apple.mpegurl")
    public String getRewrittenVariant(
            @PathVariable Long videoFileId,
            @PathVariable String quality,
            @RequestParam(name = "expires") Long expires,
            @RequestParam(name = "signature") String signature
    ) {
        String path = "/api/hls/" + videoFileId + "/" + quality + "/prog_index.m3u8";
        if (!urlSignatureService.validate(path, expires, signature)) {
            log.warn("Invalid HLS variant signature. path={}, expires={}, signature={}", path, expires, signature);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid or expired signature");
        }

        // MinIO Key: hls/{videoFileId}/{quality}/prog_index.m3u8
        String objectKey = "hls/" + videoFileId + "/" + quality + "/prog_index.m3u8";
        String raw = loadFromMinio(objectKey);

        return raw.lines()
                .map(line -> {
                    String trimmed = line.trim();
                    if (trimmed.isBlank() || trimmed.startsWith("#")) return trimmed;

                    if (trimmed.endsWith(".ts")) {
                        // Key: hls/{videoFileId}/{quality}/{segment.ts}
                        String segKey = "hls/" + videoFileId + "/" + quality + "/" + trimmed;
                        return generatePresignedUrl(segKey);
                    }

                    return trimmed;
                })
                .collect(Collectors.joining("\n")) + "\n";
    }

    private String loadFromMinio(String key) {
        try (InputStream is = internalMinioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(props.bucket())
                        .object(key)
                        .build()
        )) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (MinioException e) {
            log.error("MinIO error while fetching file. key={}", key, e);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found in storage");
        } catch (Exception e) {
            log.error("File load failed. key={}", key, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "File load failed");
        }
    }

    private String generatePresignedUrl(String objectKey) {
        try {
            return publicMinioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(props.bucket())
                            .object(objectKey)
                            .expiry(600) // 10 minutes
                            .build()
            );
        } catch (Exception e) {
            log.error("Presigned URL generation failed for {}", objectKey, e);
            return objectKey;
        }
    }
}