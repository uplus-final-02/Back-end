package org.backend.admin.upload.service;

import core.security.principal.JwtPrincipal;
import core.storage.ObjectStorageService;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.backend.admin.upload.dto.VideoUploadPresignRequest;
import org.backend.admin.upload.dto.VideoUploadPresignResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class VideoUploadPresignService {

    private final ObjectStorageService objectStorageService;

    public VideoUploadPresignResponse presign(JwtPrincipal principal, VideoUploadPresignRequest req) {
        if (principal == null) {
            throw new IllegalStateException("UNAUTHORIZED: JwtPrincipal is null");
        }
        validate(req);

        // key 규칙: videos/original/{contentId}/{uuid}.{ext}
        String objectKey = objectStorageService.buildObjectKey(
                "videos/original",
                req.contentId(),
                req.originalFilename()
        );

        var put = objectStorageService.generatePutPresignedUrl(
                objectKey,
                req.contentType(),
                Duration.ofMinutes(10)
        );

        return new VideoUploadPresignResponse(
                req.contentId(),
                put.objectKey(),
                put.url().toString(),
                put.expiresAt()
        );
    }

    private void validate(VideoUploadPresignRequest req) {
        if (req == null || req.contentId() == null) {
            throw new IllegalArgumentException("contentId는 필수입니다.");
        }
        if (!StringUtils.hasText(req.originalFilename())) {
            throw new IllegalArgumentException("originalFilename은 필수입니다.");
        }
        // contentType은 없어도 돌아가긴 하지만, 이후 확장(검증/서명 헤더) 때문에 받는 걸 추천
        if (!StringUtils.hasText(req.contentType())) {
            throw new IllegalArgumentException("contentType은 필수입니다. 예: video/mp4");
        }
    }
}