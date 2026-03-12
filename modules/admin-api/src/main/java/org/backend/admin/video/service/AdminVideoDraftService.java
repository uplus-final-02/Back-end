package org.backend.admin.video.service;

import common.enums.*;
import content.entity.Content;
import content.entity.Video;
import content.entity.VideoFile;
import content.repository.ContentRepository;
import content.repository.VideoFileRepository;
import content.repository.VideoRepository;
import core.security.principal.JwtPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.backend.admin.video.dto.AdminVideoDraftCreateResponse;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminVideoDraftService {

    private final ContentRepository contentRepository;
    private final VideoRepository videoRepository;
    private final VideoFileRepository videoFileRepository;

    public AdminVideoDraftCreateResponse createDraft(JwtPrincipal principal, Authentication authentication) {
        if (principal == null) {
            throw new IllegalArgumentException("인증 정보가 없습니다.");
        }

        Long uploaderId = resolveUploaderId(principal, authentication);

        Content content = Content.builder()
                .type(ContentType.SINGLE)
                .title("TEMP_SINGLE_TITLE")
                .description(null)
                .thumbnailUrl("TEMP_THUMBNAIL_URL")
                .status(ContentStatus.HIDDEN)
                .uploaderId(uploaderId)
                .accessLevel(ContentAccessLevel.FREE)
                .build();
        contentRepository.save(content);

        Video video = Video.builder()
                .episodeNo(1)
                .title(null)
                .description(null)
                .thumbnailUrl(null)
                .status(VideoStatus.DRAFT)
                .build();
        video.setContent(content);
        videoRepository.save(video);

        VideoFile vf = VideoFile.builder()
                .video(video)
                .originalUrl(null)
                .hlsUrl(null)
                .durationSec(0)
                .transcodeStatus(TranscodeStatus.PENDING_UPLOAD)
                .build();
        videoFileRepository.save(vf);

        return new AdminVideoDraftCreateResponse(content.getId(), video.getId(), vf.getId());
    }

    private Long resolveUploaderId(JwtPrincipal principal, Authentication authentication) {
        if (authentication != null && authentication.getAuthorities() != null) {
            boolean isAdmin = authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .anyMatch(a -> a.equals("ROLE_ADMIN"));
            if (isAdmin) return null;
        }
        return principal.getUserId();
    }
}