package org.backend.userapi.upload.service;

import core.storage.ObjectStorageService;
import core.security.principal.JwtPrincipal;
import lombok.RequiredArgsConstructor;
import org.backend.userapi.upload.dto.UserUploadPresignRequest;
import org.backend.userapi.upload.dto.UserUploadPresignResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class UserUploadPresignService {

    private final ObjectStorageService objectStorageService;

    @Transactional(readOnly = true)
    public UserUploadPresignResponse presign(JwtPrincipal principal, UserUploadPresignRequest req) {
        if (principal == null || principal.getUserId() == null) {
            throw new IllegalArgumentException("LOGIN_REQUIRED");
        }
        validate(req);

        // key 규칙: videos/user-upload/{userContentId}/{uuid}.{ext}
        String objectKey = objectStorageService.buildObjectKey(
                "videos/user-upload",
                req.userContentId(),
                req.originalFilename()
        );

        var put = objectStorageService.generatePutPresignedUrl(
                objectKey,
                req.contentType(),
                Duration.ofMinutes(10)
        );

        return new UserUploadPresignResponse(
                req.userContentId(),
                put.objectKey(),
                put.url().toString(),
                put.expiresAt()
        );
    }

    private void validate(UserUploadPresignRequest req) {
        if (req == null || req.userContentId() == null) {
            throw new IllegalArgumentException("userContentId는 필수입니다.");
        }
        if (!StringUtils.hasText(req.originalFilename())) {
            throw new IllegalArgumentException("originalFilename은 필수입니다.");
        }
        if (!StringUtils.hasText(req.contentType())) {
            throw new IllegalArgumentException("contentType은 필수입니다. 예: video/mp4");
        }
    }
}