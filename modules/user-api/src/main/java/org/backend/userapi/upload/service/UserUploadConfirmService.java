package org.backend.userapi.upload.service;

import common.enums.TranscodeStatus;
import common.enums.VideoStatus;
import content.entity.UserContent;
import content.entity.UserVideoFile;
import content.repository.UserContentRepository;
import content.repository.UserVideoFileRepository;
import core.events.video.VideoTranscodeEventPublisher;
import core.events.video.VideoTranscodeRequestedEvent;
import core.security.principal.JwtPrincipal;
import core.storage.ObjectNotFoundException;
import core.storage.ObjectStorageService;
import core.storage.StorageException;
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

        if (!uc.getUploaderId().equals(principal.getUserId())) {
            throw new IllegalArgumentException("FORBIDDEN: not owner");
        }

        var stat = safeStat(req.objectKey());
        if (stat.size() <= 0) {
            throw new IllegalStateException("UPLOAD_NOT_COMPLETED");
        }

        UserVideoFile uvf = userVideoFileRepository.findByContent_Id(uc.getId())
                .orElseThrow(() -> new IllegalStateException("USER_VIDEO_FILE_NOT_FOUND: contentId=" + uc.getId()));

        uvf.updateOriginalKey(req.objectKey());
        uvf.updateTranscodeStatus(TranscodeStatus.WAITING);

        uvf.updateVideoStatus(VideoStatus.PRIVATE);

        uc.updateContentStatus(common.enums.ContentStatus.HIDDEN);

        videoTranscodeEventPublisher.publish(
                VideoTranscodeRequestedEvent.of(
                        uc.getId(),
                        null,
                        uvf.getId(),
                        uvf.getOriginalUrl()
                )
        );

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