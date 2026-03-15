package org.backend.userapi.content.service;

import common.enums.ContentStatus;
import common.enums.VideoStatus;
import content.entity.UserContent;
import content.entity.UserVideoFile;
import content.repository.UserContentRepository;
import content.repository.UserVideoFileRepository;
import core.security.principal.JwtPrincipal;
import lombok.RequiredArgsConstructor;
import org.backend.userapi.content.dto.UserContentDeleteResponse;
import org.backend.userapi.content.dto.UserContentUpdateRequest;
import org.backend.userapi.content.dto.UserContentUpdateResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class UserContentService {

    private final UserContentRepository userContentRepository;
    private final UserVideoFileRepository userVideoFileRepository;

    @Transactional
    public UserContentUpdateResponse updateMetadata(JwtPrincipal principal, Long userContentId, UserContentUpdateRequest req) {
        requireLogin(principal);

        UserContent uc = userContentRepository.findById(userContentId)
                .orElseThrow(() -> new IllegalArgumentException("USER_CONTENT_NOT_FOUND: " + userContentId));

        if (!uc.getUploaderId().equals(principal.getUserId())) {
            throw new IllegalArgumentException("FORBIDDEN: not owner");
        }

        if (uc.getContentStatus() == ContentStatus.DELETED) {
            throw new IllegalStateException("USER_CONTENT_ALREADY_DELETED");
        }

        if (req != null) {
            if (StringUtils.hasText(req.title())) {
                uc.updateTitle(req.title());
            }
            if (req.description() != null) {
                uc.updateDescription(req.description());
            }

            if (req.contentStatus() != null) {
                if (req.contentStatus() == ContentStatus.DELETED) {
                    throw new IllegalArgumentException("INVALID_CONTENT_STATUS: use DELETE API");
                }
                uc.updateContentStatus(req.contentStatus());
            }
        }

        UserVideoFile uvf = userVideoFileRepository.findByContent_Id(uc.getId())
                .orElseThrow(() -> new IllegalStateException("USER_VIDEO_FILE_NOT_FOUND: contentId=" + uc.getId()));

        if (req != null && req.videoStatus() != null) {
            if (req.videoStatus() == VideoStatus.PUBLIC && uvf.getTranscodeStatus() != common.enums.TranscodeStatus.DONE) {
                throw new IllegalStateException("VIDEO_NOT_READY: transcodeStatus=" + uvf.getTranscodeStatus());
            }
            uvf.updateVideoStatus(req.videoStatus());

            if (req.videoStatus() == VideoStatus.PUBLIC) {
                uc.updateContentStatus(ContentStatus.ACTIVE);
            } else if (req.videoStatus() == VideoStatus.PRIVATE) {
                uc.updateContentStatus(ContentStatus.HIDDEN);
            }
        }

        return new UserContentUpdateResponse(
                uc.getId(),
                uc.getContentStatus(),
                uvf.getVideoStatus()
        );
    }

    @Transactional
    public UserContentDeleteResponse delete(JwtPrincipal principal, Long userContentId) {
        requireLogin(principal);

        UserContent uc = userContentRepository.findById(userContentId)
                .orElseThrow(() -> new IllegalArgumentException("USER_CONTENT_NOT_FOUND: " + userContentId));

        if (!uc.getUploaderId().equals(principal.getUserId())) {
            throw new IllegalArgumentException("FORBIDDEN: not owner");
        }

        if (uc.getContentStatus() == ContentStatus.DELETED) {
            return UserContentDeleteResponse.of(uc.getId(), uc.getContentStatus());
        }

        uc.markDeleted();

        userVideoFileRepository.findByContent_Id(uc.getId())
                .ifPresent(vf -> vf.updateVideoStatus(VideoStatus.PRIVATE));

        return UserContentDeleteResponse.of(uc.getId(), uc.getContentStatus());
    }

    private void requireLogin(JwtPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new IllegalArgumentException("LOGIN_REQUIRED");
        }
    }
}