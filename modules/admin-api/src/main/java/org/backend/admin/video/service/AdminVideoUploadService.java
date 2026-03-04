package org.backend.admin.video.service;

import common.enums.TranscodeStatus;
import common.enums.VideoStatus;
import content.entity.Content;
import content.entity.Video;
import content.entity.VideoFile;
import content.repository.ContentRepository;
import content.repository.VideoFileRepository;
import content.repository.VideoRepository;
import core.events.video.VideoTranscodeEventPublisher;
import core.events.video.VideoTranscodeRequestedEvent;
import core.storage.StorageException;
import lombok.RequiredArgsConstructor;
import org.backend.admin.video.dto.AdminVideoUploadConfirmRequest;
import org.backend.admin.video.dto.AdminVideoUploadConfirmResponse;
import org.springframework.stereotype.Service;
import core.storage.ObjectStorageService;
import core.storage.ObjectNotFoundException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.backend.admin.exception.ContentNotFoundException;
import org.backend.admin.exception.UploadNotCompletedException;

@Service
@RequiredArgsConstructor
public class AdminVideoUploadService {

    private final ContentRepository contentRepository;
    private final VideoRepository videoRepository;
    private final VideoFileRepository videoFileRepository;

    private final ObjectStorageService objectStorageService;

    private final VideoTranscodeEventPublisher videoTranscodeEventPublisher;

    @Transactional
    public AdminVideoUploadConfirmResponse confirmUpload(AdminVideoUploadConfirmRequest req) {
        validate(req);

        var stat = safeStat(req.objectKey());
        if (stat.size() <= 0) {
            throw new UploadNotCompletedException(); // 0바이트도 업로드 미완료
        }

        Video video = videoRepository.findById(req.videoId())
                .orElseThrow(() -> new RuntimeException("VIDEO_NOT_FOUND"));

        if (!video.getContent().getId().equals(req.contentId())) {
            throw new RuntimeException("VIDEO_CONTENT_MISMATCH");
        }

        if (video.getStatus() != VideoStatus.DRAFT) {
            throw new RuntimeException("VIDEO_NOT_DRAFT");
        }

        video.updateInfo(req.title(), req.description());
        video.updateStatus(VideoStatus.PRIVATE);

        VideoFile vf = videoFileRepository.findByVideoId(video.getId())
                .orElseThrow(() -> new RuntimeException("VIDEO_FILE_NOT_FOUND"));

        vf.updateOriginalKey(req.objectKey());
        vf.updateTranscodeStatus(TranscodeStatus.WAITING);

        videoTranscodeEventPublisher.publish(
                VideoTranscodeRequestedEvent.of(
                        video.getContent().getId(),
                        video.getId(),
                        vf.getId(),
                        vf.getOriginalUrl()
                )
        );

        return new AdminVideoUploadConfirmResponse(
                video.getContent().getId(),
                video.getId(),
                vf.getId(),
                vf.getOriginalUrl(),
                vf.getTranscodeStatus().name()
        );
    }

    private void validate(AdminVideoUploadConfirmRequest req) {
        if (req == null || req.contentId() == null) {
            throw new IllegalArgumentException("contentId는 필수입니다.");
        }
        if (req.videoId() == null) {
            throw new IllegalArgumentException("videoId는 필수입니다.");
        }
        if (!StringUtils.hasText(req.objectKey())) {
            throw new IllegalArgumentException("objectKey는 필수입니다.");
        }
    }

    private ObjectStorageService.ObjectStat safeStat(String objectKey) {
        try {
            return objectStorageService.statObject(objectKey);
        } catch (ObjectNotFoundException e) {
            throw new UploadNotCompletedException();
        } catch (StorageException e) {
            String msg = e.getMessage();
            if (msg != null && (msg.contains("NoSuchKey") || msg.contains("Object does not exist"))) {
                throw new UploadNotCompletedException();
            }
            throw e;
        }
    }
}