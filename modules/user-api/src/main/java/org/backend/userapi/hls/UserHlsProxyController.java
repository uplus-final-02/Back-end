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
public class UserHlsProxyController {

    @Qualifier("internalMinioClient")
    private final MinioClient internalMinioClient;

    @Qualifier("publicMinioClient")
    private final MinioClient publicMinioClient;

    private final StorageProperties props;
    private final LocalUrlSignatureService urlSignatureService;

    // 사용자용 HLS 프록시: m3u8을 읽어서 세그먼트 URL을 Presigned URL로 변환
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
        int expirySec = 600; // 사용자 재생용 URL은 10분간 유효

        // 1) MinIO에서 master.m3u8 읽기
        String raw;
        try (InputStream is = internalMinioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(props.bucket())
                        .object(masterKey)
                        .build()
        )) {
            raw = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (MinioException e) {
            log.error("MinIO error while fetching master file. key={}", masterKey, e);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Video not found in storage");
        } catch (Exception e) {
            log.error("HLS master file load failed. key={}", masterKey, e);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "HLS Master file not found");
        }

        // 2) URL Rewrite
        String basePrefix = "hls/" + videoFileId + "/";

        return raw.lines()
                .map(line -> {
                    String trimmed = line.trim();
                    if (trimmed.isBlank() || trimmed.startsWith("#")) return trimmed;

                    String segKey = trimmed.startsWith("http") ? null : basePrefix + trimmed;
                    if (segKey == null) return trimmed;

                    try {
                        return publicMinioClient.getPresignedObjectUrl(
                                GetPresignedObjectUrlArgs.builder()
                                        .method(Method.GET)
                                        .bucket(props.bucket())
                                        .object(segKey)
                                        .expiry(expirySec)
                                        .build()
                        );
                    } catch (Exception e) {
                        log.error("Presigned URL generation failed for {}", segKey, e);
                        return trimmed;
                    }
                })
                .collect(Collectors.joining("\n")) + "\n";
    }
}