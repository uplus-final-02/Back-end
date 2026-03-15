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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserUploadDraftService {

    private final ContentRepository contentRepository; // 관리자 콘텐츠 검증용(기존 contents)
    private final UserContentRepository userContentRepository;
    private final UserVideoFileRepository userVideoFileRepository;

    @Transactional
    public org.backend.userapi.upload.dto.UserUploadDraftResponse createDraft(JwtPrincipal principal, Long parentContentId) {
        if (principal == null || principal.getUserId() == null) {
            throw new IllegalArgumentException("LOGIN_REQUIRED");
        }
        if (parentContentId == null) {
            throw new IllegalArgumentException("parentContentId는 필수입니다.");
        }

        Content parent = contentRepository.findById(parentContentId)
                .orElseThrow(() -> new IllegalArgumentException("PARENT_CONTENT_NOT_FOUND: " + parentContentId));

        // 부모 콘텐츠는 "관리자 업로드 컨텐츠"만 선택 가능: 여기서는 uploaderId == null(관리자)로 판단하는 기존 정책을 사용
        if (parent.getUploaderId() != null) {
            throw new IllegalArgumentException("INVALID_PARENT_CONTENT: 관리자 콘텐츠만 선택 가능합니다.");
        }
        if (parent.getStatus() != ContentStatus.ACTIVE) {
            throw new IllegalArgumentException("PARENT_CONTENT_NOT_ACTIVE: status=" + parent.getStatus());
        }

        // 유저 콘텐츠 생성 (타입 없음)
        UserContent uc = UserContent.builder()
                .parentContentId(parentContentId)
                .title(parent.getTitle())               // 기본값: 부모 타이틀 복사(원하면 "UNTITLED"로)
                .thumbnailUrl(parent.getThumbnailUrl()) // 기본값: 부모 썸네일 복사
                .contentStatus(ContentStatus.ACTIVE)
                .videoStatus(VideoStatus.DRAFT)
                .uploaderId(principal.getUserId())
                .accessLevel(ContentAccessLevel.FREE)
                .build();

        uc = userContentRepository.save(uc);

        UserVideoFile uvf = UserVideoFile.builder()
                .userContent(uc)
                .originalUrl(null)
                .hlsUrl(null)
                .durationSec(0)
                .transcodeStatus(common.enums.TranscodeStatus.PENDING_UPLOAD)
                .build();

        uvf = userVideoFileRepository.save(uvf);

        return new org.backend.userapi.upload.dto.UserUploadDraftResponse(uc.getId(), uvf.getId());
    }
}