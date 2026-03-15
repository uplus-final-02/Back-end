package org.backend.userapi.upload.service;

import common.enums.TranscodeStatus;
import content.entity.UserContent;
import content.entity.UserVideoFile;
import content.repository.UserContentRepository;
import content.repository.UserVideoFileRepository;
import core.events.video.VideoTranscodeEventPublisher;
import core.events.video.VideoTranscodeRequestedEvent;
import core.storage.ObjectNotFoundException;
import core.storage.ObjectStorageService;
import core.storage.StorageException;
import core.security.principal.JwtPrincipal;
import lombok.RequiredArgsConstructor;
import org.backend.userapi.upload.dto.UserUploadConfirmRequest;
import org.backend.userapi.upload.dto.UserUploadConfirmResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class UserUploadConfirmService {

    private final UserContentRepository userContentRepository;
    private final UserVideoFileRepository userVideoFileRepository;
    private final ObjectStorageService objectStorageService;
    private final VideoTranscodeEventPublisher videoTranscodeEventPublisher;

    @Transactional
    public UserUploadConfirmResponse confirm(JwtPrincipal principal, UserUploadConfirmRequest req) {
        if (principal == null || principal.getUserId() == null) {
            throw new IllegalArgumentException("LOGIN_REQUIRED");
        }
        validate(req);

        UserContent uc = userContentRepository.findById(req.userContentId())
                .orElseThrow(() -> new IllegalArgumentException("USER_CONTENT_NOT_FOUND: " + req.userContentId()));

        // 본인 소유 검증
        if (!uc.getUploaderId().equals(principal.getUserId())) {
            throw new IllegalArgumentException("FORBIDDEN: not owner");
        }

        // 업로드 완료 검증 (0byte/미존재)
        var stat = safeStat(req.objectKey());
        if (stat.size() <= 0) {
            throw new IllegalStateException("UPLOAD_NOT_COMPLETED");
        }

        UserVideoFile uvf = userVideoFileRepository.findByUserContentId(uc.getId())
                .orElseThrow(() -> new IllegalStateException("USER_VIDEO_FILE_NOT_FOUND"));

        // originalKey 저장 + WAITING
        uvf.updateOriginalKey(req.objectKey());
        uvf.updateTranscodeStatus(TranscodeStatus.WAITING);

        // 이후 worker가 ffprobe로 duration 측정 → 180초 초과면 FAILED로 처리(추후 worker 로직에 추가 예정)
        videoTranscodeEventPublisher.publish(
                VideoTranscodeRequestedEvent.of(
                        uc.getId(),          // contentId 자리에 userContentId를 넣어도 되고(워커는 경로용으로만 쓰면 됨)
                        null,                // videoId 없음
                        uvf.getId(),
                        uvf.getOriginalUrl()
                )
        );

        // user side status는 PRIVATE로 두는게 안전(트랜스코딩 끝나고 공개)
        uc.markVideoPrivate();

        return new UserUploadConfirmResponse(
                uc.getId(),
                uvf.getId(),
                uvf.getOriginalUrl(),
                uvf.getTranscodeStatus().name()
        );
    }

    private void validate(UserUploadConfirmRequest req) {
        if (req == null || req.userContentId() == null) {
            throw new IllegalArgumentException("userContentId는 필수입니다.");
        }
        if (!StringUtils.hasText(req.objectKey())) {
            throw new IllegalArgumentException("objectKey는 필수입니다.");
        }
    }

    private ObjectStorageService.ObjectStat safeStat(String objectKey) {
        try {
            return objectStorageService.statObject(objectKey);
        } catch (ObjectNotFoundException e) {
            throw new IllegalStateException("UPLOAD_NOT_COMPLETED");
        } catch (StorageException e) {
            String msg = e.getMessage();
            if (msg != null && (msg.contains("NoSuchKey") || msg.contains("Object does not exist"))) {
                throw new IllegalStateException("UPLOAD_NOT_COMPLETED");
            }
            throw e;
        }
    }
}