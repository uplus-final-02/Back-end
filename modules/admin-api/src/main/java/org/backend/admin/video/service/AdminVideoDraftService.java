package org.backend.admin.video.service;

import common.enums.*;
import content.entity.Content;
import content.entity.Video;
import content.entity.VideoFile;
import content.repository.ContentRepository;
import content.repository.VideoFileRepository;
import content.repository.VideoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.backend.admin.video.dto.AdminVideoDraftCreateRequest;
import org.backend.admin.video.dto.AdminVideoDraftCreateResponse;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminVideoDraftService {

    private final ContentRepository contentRepository;
    private final VideoRepository videoRepository;
    private final VideoFileRepository videoFileRepository;

    public AdminVideoDraftCreateResponse createDraft(AdminVideoDraftCreateRequest request) {
        if (request == null || request.uploaderId() == null) {
            throw new IllegalArgumentException("uploaderId is required");
        }

        // contents: title/thumbnail_url NOT NULL 이므로 임시값 필요
        Content content = Content.builder()
                .type(ContentType.SINGLE)
                .title("UNTITLED")
                .description(null)
                .thumbnailUrl("")
                .status(ContentStatus.HIDDEN)
                .uploaderId(request.uploaderId())
                .accessLevel(ContentAccessLevel.FREE)
                .build();
        contentRepository.save(content);

        // single 이므로 episodeNo=1
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
}