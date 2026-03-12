package org.backend.admin.video.service;

import common.enums.VideoStatus;
import content.entity.Video;
import content.repository.VideoRepository;
import core.security.principal.JwtPrincipal;
import core.storage.ObjectStorageService;
import lombok.RequiredArgsConstructor;
import org.backend.admin.exception.UploadNotCompletedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class AdminSeriesEpisodePresignService {

    private final VideoRepository videoRepository;
    private final ObjectStorageService objectStorageService;

    @Transactional(readOnly = true)
    public org.backend.admin.video.dto.AdminEpisodePresignResponse presignPutUrl(
            JwtPrincipal principal,
            Long seriesId,
            Long videoId,
            org.backend.admin.video.dto.AdminEpisodePresignRequest req
    ) {
        if (principal == null) {
            throw new IllegalStateException("UNAUTHORIZED: JwtPrincipal is null");
        }

        validate(req);

        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new IllegalArgumentException("VIDEO_NOT_FOUND: videoId=" + videoId));

        Long contentId = video.getContent().getId();
        if (!contentId.equals(seriesId)) {
            throw new IllegalArgumentException("VIDEO_NOT_IN_SERIES: seriesId=" + seriesId + ", videoId=" + videoId);
        }

        if (video.getStatus() != VideoStatus.DRAFT) {
            throw new IllegalArgumentException("INVALID_VIDEO_STATUS: DRAFT가 아닙니다. status=" + video.getStatus());
        }

        String objectKey = objectStorageService.buildObjectKey(
                "videos/original",
                seriesId,
                req.originalFilename()
        );

        var put = objectStorageService.generatePutPresignedUrl(
                objectKey,
                req.contentType(),
                Duration.ofMinutes(10)
        );

        return new org.backend.admin.video.dto.AdminEpisodePresignResponse(
                seriesId,
                videoId,
                put.objectKey(),
                put.url().toString(),
                put.expiresAt()
        );
    }

    private void validate(org.backend.admin.video.dto.AdminEpisodePresignRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("요청 본문이 필요합니다.");
        }
        if (!StringUtils.hasText(req.originalFilename())) {
            throw new IllegalArgumentException("originalFilename은 필수입니다.");
        }
        if (!StringUtils.hasText(req.contentType())) {
            throw new IllegalArgumentException("contentType은 필수입니다. (예: video/mp4)");
        }
    }
}