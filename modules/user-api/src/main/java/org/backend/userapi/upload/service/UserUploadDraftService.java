package org.backend.userapi.upload.service;

import common.enums.ContentAccessLevel;
import common.enums.ContentStatus;
import common.enums.VideoStatus;
import content.entity.Content;
import content.entity.UserContent;
import content.entity.UserVideoFile;
import content.repository.ContentRepository;
import content.repository.UserContentRepository;
import content.repository.UserVideoFileRepository;
import core.security.principal.JwtPrincipal;
import lombok.RequiredArgsConstructor;
import org.backend.userapi.upload.dto.UserUploadDraftResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserUploadDraftService {

    private static final String TEMP_USER_TITLE = "TEMP_USER_TITLE";

    private final ContentRepository contentRepository;
    private final UserContentRepository userContentRepository;
    private final UserVideoFileRepository userVideoFileRepository;

    @Transactional
    public UserUploadDraftResponse createDraft(JwtPrincipal principal, Long parentContentId) {
        if (principal == null || principal.getUserId() == null) {
            throw new IllegalArgumentException("LOGIN_REQUIRED");
        }
        if (parentContentId == null) {
            throw new IllegalArgumentException("parentContentId는 필수입니다.");
        }

        Content parent = contentRepository.findById(parentContentId)
                .orElseThrow(() -> new IllegalArgumentException("PARENT_CONTENT_NOT_FOUND: " + parentContentId));

        if (parent.getUploaderId() != null) {
            throw new IllegalArgumentException("INVALID_PARENT_CONTENT: 관리자 콘텐츠만 선택 가능합니다.");
        }
        if (parent.getStatus() != ContentStatus.ACTIVE) {
            throw new IllegalArgumentException("PARENT_CONTENT_NOT_ACTIVE: status=" + parent.getStatus());
        }

        UserContent uc = UserContent.builder()
                .parentContent(parent)
                .title(TEMP_USER_TITLE)
                .description(null)
                .contentStatus(ContentStatus.HIDDEN)
                .uploaderId(principal.getUserId())
                .accessLevel(ContentAccessLevel.FREE)
                .build();

        uc = userContentRepository.save(uc);

        UserVideoFile uvf = UserVideoFile.builder()
                .content(uc)
                .originalUrl(null)
                .hlsUrl(null)
                .durationSec(0)
                .videoStatus(VideoStatus.DRAFT)
                .transcodeStatus(common.enums.TranscodeStatus.PENDING_UPLOAD)
                .build();

        uvf = userVideoFileRepository.save(uvf);

        return new UserUploadDraftResponse(uc.getId(), uvf.getId());
    }
}