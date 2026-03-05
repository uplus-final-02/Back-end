package core.storage;

import java.net.URL;
import java.time.Duration;
import java.time.Instant;

public interface ObjectStorageService {

    PresignedUrlResult generatePutPresignedUrl(String objectKey, String contentType, Duration expiry);

    PresignedUrlResult generateGetPresignedUrl(String objectKey, Duration expiry);

    /**
     * 공개 리소스(예: thumbnails/) 용 고정 URL 생성
     * - publicBaseUrl + /bucket + /objectKey
     * - 버킷을 public로 열었거나 prefix 정책으로 public read가 가능한 경우에만 사용
     */
    String buildPublicUrl(String objectKey);

    /**
     * 팀 정책 키 규칙 추천
     *  - videos/original/{contentId}/{uuid}.{ext}
     *  - thumbnails/{contentId}/{uuid}.{ext}
     */
    String buildObjectKey(String prefix, Long contentId, String originalFilename);

    ObjectStat statObject(String objectKey);

    record PresignedUrlResult(
            String objectKey,
            URL url,
            Instant expiresAt
    ) {}

    record ObjectStat(
            String objectKey,
            long size,
            String etag,
            String contentType
    ) {}
}