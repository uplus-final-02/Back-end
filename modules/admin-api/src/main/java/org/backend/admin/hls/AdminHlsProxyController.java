package org.backend.admin.hls;

import core.storage.config.StorageProperties;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/hls")
public class AdminHlsProxyController {

    @Qualifier("internalMinioClient")
    private final MinioClient internalMinioClient;

    @Qualifier("publicMinioClient")
    private final MinioClient publicMinioClient;

    private final StorageProperties props;

    // 개발용: m3u8을 presigned 세그먼트 URL로 rewrite 해서 반환
    @GetMapping(value = "/{videoFileId}/master.m3u8", produces = "application/vnd.apple.mpegurl")
    public String getRewrittenMaster(@PathVariable Long videoFileId,
                                     @RequestParam(defaultValue = "600") int expirySec) throws Exception {

        String masterKey = "hls/" + videoFileId + "/master.m3u8";

        // 1) MinIO에서 master.m3u8 텍스트 읽기
        String raw;
        try (InputStream is = internalMinioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(props.bucket())
                        .object(masterKey)
                        .build()
        )) {
            raw = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        // 2) m3u8 각 라인 처리: #으로 시작하는 메타라인은 그대로, 파일명(ts 등)은 presigned로 치환
        String basePrefix = "hls/" + videoFileId + "/";

        String rewritten = raw.lines()
                .map(line -> {
                    String trimmed = line.trim();
                    if (trimmed.isBlank()) return trimmed;
                    if (trimmed.startsWith("#")) return trimmed;

                    // 상대경로(seg_00000.ts)면 basePrefix 붙여서 objectKey 생성
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
                        throw new RuntimeException("PRESIGN_FAILED for " + segKey, e);
                    }
                })
                .collect(Collectors.joining("\n"));

        return rewritten + "\n";
    }
}