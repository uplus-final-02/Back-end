package org.backend.admin.hls;

import core.storage.MinioBucketInitializer;
import core.storage.StorageException;
import core.storage.config.StorageProperties;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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
    private final MinioBucketInitializer bucketInitializer;

    /**
     * 1) master.m3u8 반환
     * - master 안의 variant playlist 경로(v0/prog_index.m3u8 등)를
     *   우리 서버의 variant 프록시 URL로 바꿔서 내려줌
     * - Degraded Mode 시 assertAvailable()에서 즉시 예외 발생
     * - MinIO 읽기 실패 시 StorageException으로 래핑
     */
    @GetMapping(value = "/{videoFileId}/master.m3u8", produces = "application/vnd.apple.mpegurl")
    public String getRewrittenMaster(@PathVariable Long videoFileId,
                                     @RequestParam(defaultValue = "600") int expirySec) {

        bucketInitializer.assertAvailable();

        String masterKey = "hls/" + videoFileId + "/master.m3u8";
        String raw = readObjectAsString(masterKey);

        String baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                .build()
                .toUriString();

        MasterParseResult parsed = parseMaster(raw);

        // BANDWIDTH 오름차순 정렬
        parsed.variants.sort((a, b) -> Long.compare(a.bandwidth, b.bandwidth));

        StringBuilder sb = new StringBuilder();
        for (String h : parsed.headerLines) {
            sb.append(h).append("\n");
        }

        for (VariantBlock v : parsed.variants) {
            sb.append(v.streamInfLine).append("\n");

            String variant = extractVariantFromUri(v.uriLine);
            if (variant == null) {
                // 예상하지 못한 URI면 원본 유지
                sb.append(v.uriLine).append("\n\n");
                continue;
            }

            sb.append(baseUrl)
                    .append("/admin/hls/")
                    .append(videoFileId)
                    .append("/")
                    .append(variant)
                    .append("/prog_index.m3u8?expirySec=")
                    .append(expirySec)
                    .append("\n\n");
        }

        return sb.toString();
    }

    /**
     * 2) variant playlist(v0/v1/v2) 반환
     * - variant m3u8 안의 ts 라인을 presigned URL로 치환
     * - Degraded Mode 시 assertAvailable()에서 즉시 예외 발생
     */
    @GetMapping(value = "/{videoFileId}/{variant}/prog_index.m3u8", produces = "application/vnd.apple.mpegurl")
    public String getRewrittenVariant(@PathVariable Long videoFileId,
                                      @PathVariable String variant,
                                      @RequestParam(defaultValue = "600") int expirySec) {

        bucketInitializer.assertAvailable();

        if (!variant.equals("v0") && !variant.equals("v1") && !variant.equals("v2")) {
            throw new IllegalArgumentException("invalid variant: " + variant);
        }

        String variantKey = "hls/" + videoFileId + "/" + variant + "/prog_index.m3u8";
        String raw = readObjectAsString(variantKey);

        String basePrefix = "hls/" + videoFileId + "/" + variant + "/";

        String rewritten = raw.lines()
                .map(line -> {
                    String t = line.trim();
                    if (t.isBlank()) return t;
                    if (t.startsWith("#")) return t;

                    String segKey = basePrefix + t;

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
                        throw new StorageException("HLS presigned URL 생성 실패: " + segKey, e);
                    }
                })
                .collect(Collectors.joining("\n"));

        return rewritten + "\n";
    }

    private String readObjectAsString(String objectKey) {
        try (InputStream is = internalMinioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(props.bucket())
                        .object(objectKey)
                        .build()
        )) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new StorageException("HLS object 읽기 실패: " + objectKey, e);
        }
    }

    private static class MasterParseResult {
        List<String> headerLines = new ArrayList<>();
        List<VariantBlock> variants = new ArrayList<>();
    }

    private static class VariantBlock {
        String streamInfLine;
        String uriLine;
        long bandwidth;
    }

    private MasterParseResult parseMaster(String raw) {
        MasterParseResult result = new MasterParseResult();

        List<String> lines = raw.lines().toList();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isBlank()) continue;

            if (line.startsWith("#EXT-X-STREAM-INF:")) {
                VariantBlock vb = new VariantBlock();
                vb.streamInfLine = line;
                vb.bandwidth = parseBandwidth(line);

                String uri = null;
                int j = i + 1;
                while (j < lines.size()) {
                    String next = lines.get(j).trim();
                    if (next.isBlank()) {
                        j++;
                        continue;
                    }
                    if (next.startsWith("#")) {
                        j++;
                        continue;
                    }
                    uri = next;
                    break;
                }

                if (uri == null) {
                    result.headerLines.add(line);
                } else {
                    vb.uriLine = uri;
                    result.variants.add(vb);
                    i = j;
                }
            } else {
                result.headerLines.add(line);
            }
        }

        return result;
    }

    private long parseBandwidth(String streamInfLine) {
        int idx = streamInfLine.indexOf("BANDWIDTH=");
        if (idx < 0) return Long.MAX_VALUE;

        int start = idx + "BANDWIDTH=".length();
        int end = start;
        while (end < streamInfLine.length() && Character.isDigit(streamInfLine.charAt(end))) {
            end++;
        }

        String num = streamInfLine.substring(start, end);
        try {
            return Long.parseLong(num);
        } catch (Exception e) {
            return Long.MAX_VALUE;
        }
    }

    private String extractVariantFromUri(String uriLine) {
        String t = uriLine.trim();

        if (t.startsWith("v0/")) return "v0";
        if (t.startsWith("v1/")) return "v1";
        if (t.startsWith("v2/")) return "v2";

        if (t.contains("/v0/")) return "v0";
        if (t.contains("/v1/")) return "v1";
        if (t.contains("/v2/")) return "v2";

        return null;
    }
}