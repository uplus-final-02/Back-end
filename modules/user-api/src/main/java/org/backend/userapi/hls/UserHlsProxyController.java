package org.backend.userapi.hls;

import common.enums.ContentAccessLevel;
import core.security.principal.JwtPrincipal;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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

    @GetMapping(value = "/{videoFileId}/master.m3u8", produces = "application/vnd.apple.mpegurl")
    public String getRewrittenMaster(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long videoFileId,
            @RequestParam(name = "expires") Long expires,
            @RequestParam(name = "signature") String signature
    ) {
        String path = "/api/hls/" + videoFileId + "/master.m3u8";
        if (!urlSignatureService.validate(path, expires, signature)) {
            log.warn("Invalid HLS signature. path={}, expires={}, signature={}", path, expires, signature);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid or expired signature");
        }

        ContentAccessLevel level = resolveUserLevel(principal);
        int maxAllowedVariantIndex = maxVariantByLevel(level);

        String masterKey = "hls/" + videoFileId + "/master.m3u8";
        String raw = loadFromMinio(masterKey);

        List<String> lines = raw.lines().toList();
        List<String> out = new ArrayList<>(lines.size());

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();

            if (trimmed.startsWith("#EXT-X-STREAM-INF:")) {
                int j = i + 1;
                String uri = null;
                while (j < lines.size()) {
                    String t = lines.get(j).trim();
                    if (t.isBlank() || t.startsWith("#")) {
                        j++;
                        continue;
                    }
                    uri = t;
                    break;
                }

                if (uri == null) {
                    out.add(line);
                    continue;
                }

                Integer variantIndex = parseVariantIndex(uri);
                if (variantIndex == null) {
                    out.add(line);
                    out.add(uri);
                    i = j;
                    continue;
                }

                if (variantIndex < maxAllowedVariantIndex) {
                    i = j;
                    continue;
                }

                out.add(line);

                String variantPath = "/api/hls/" + videoFileId + "/" + uri;
                String newSig = urlSignatureService.sign(variantPath, expires);
                out.add(variantPath + "?expires=" + expires + "&signature=" + newSig);

                i = j;
                continue;
            }

            if (trimmed.isBlank()) {
                out.add(line);
                continue;
            }

            if (!trimmed.startsWith("#") && trimmed.endsWith(".ts")) {
                String segKey = "hls/" + videoFileId + "/" + trimmed;
                out.add(generatePresignedUrl(segKey));
                continue;
            }

            out.add(line);
        }

        return out.stream().collect(Collectors.joining("\n")) + "\n";
    }

    @GetMapping(value = "/{videoFileId}/{quality}/prog_index.m3u8", produces = "application/vnd.apple.mpegurl")
    public String getRewrittenVariant(
            @AuthenticationPrincipal JwtPrincipal principal,
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

        ContentAccessLevel level = resolveUserLevel(principal);
        int maxAllowedVariantIndex = maxVariantByLevel(level);

        Integer reqVariantIndex = parseVariantIndex(quality + "/prog_index.m3u8");
        if (reqVariantIndex == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid quality: " + quality);
        }
        if (reqVariantIndex < maxAllowedVariantIndex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Quality not allowed by plan");
        }

        String objectKey = "hls/" + videoFileId + "/" + quality + "/prog_index.m3u8";
        String raw = loadFromMinio(objectKey);

        return raw.lines()
                .map(line -> {
                    String trimmed = line.trim();
                    if (trimmed.isBlank() || trimmed.startsWith("#")) return trimmed;

                    if (trimmed.endsWith(".ts")) {
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
                            .expiry(600)
                            .build()
            );
        } catch (Exception e) {
            log.error("Presigned URL generation failed for {}", objectKey, e);
            return objectKey;
        }
    }

    private ContentAccessLevel resolveUserLevel(JwtPrincipal principal) {
        if (principal == null) return ContentAccessLevel.FREE;

        if (principal.isUplus()) return ContentAccessLevel.UPLUS;
        if (principal.isPaid()) return ContentAccessLevel.BASIC;

        return ContentAccessLevel.FREE;
    }

    private int maxVariantByLevel(ContentAccessLevel level) {
        return switch (level) {
            case UPLUS -> 0;
            case BASIC -> 1;
            case FREE -> 2;
        };
    }

    private Integer parseVariantIndex(String uriOrQuality) {
        if (uriOrQuality == null) return null;
        String t = uriOrQuality.trim();

        if (t.startsWith("v") && t.length() >= 2 && Character.isDigit(t.charAt(1))) {
            int i = 1;
            while (i < t.length() && Character.isDigit(t.charAt(i))) i++;
            try {
                return Integer.parseInt(t.substring(1, i));
            } catch (Exception ignore) {
                return null;
            }
        }

        int idx = t.indexOf("/v");
        if (idx >= 0 && idx + 2 < t.length() && Character.isDigit(t.charAt(idx + 2))) {
            int i = idx + 2;
            while (i < t.length() && Character.isDigit(t.charAt(i))) i++;
            try {
                return Integer.parseInt(t.substring(idx + 2, i));
            } catch (Exception ignore) {
                return null;
            }
        }

        return null;
    }
}