package org.backend.admin.video.service;

import common.enums.ContentType;
import common.enums.TranscodeStatus;
import common.enums.VideoStatus;
import content.entity.Content;
import content.entity.Video;
import content.entity.VideoFile;
import content.repository.ContentRepository;
import content.repository.VideoFileRepository;
import content.repository.VideoRepository;
import core.storage.ObjectNotFoundException;
import core.storage.ObjectStorageService;
import core.storage.StorageException;
import lombok.RequiredArgsConstructor;
import org.backend.admin.exception.ContentNotFoundException;
import org.backend.admin.exception.UploadNotCompletedException;
import org.backend.admin.video.dto.AdminSeriesEpisodeConfirmRequest;
import org.backend.admin.video.dto.AdminSeriesEpisodeConfirmResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AdminSeriesEpisodeUploadService {

    private final ContentRepository contentRepository;
    private final VideoRepository videoRepository;
    private final VideoFileRepository videoFileRepository;
    private final ObjectStorageService objectStorageService;

    @Transactional
    public AdminSeriesEpisodeConfirmResponse confirmUpload(Long seriesId, AdminSeriesEpisodeConfirmRequest req) {
        validate(seriesId, req);

        Content series = contentRepository.findById(seriesId)
                .orElseThrow(ContentNotFoundException::new);

        if (series.getType() != ContentType.SERIES) {
            throw new IllegalArgumentException("INVALID_CONTENT_TYPE: SERIES 콘텐츠가 아닙니다.");
        }

        Video video = videoRepository.findById(req.videoId())
                .orElseThrow(() -> new IllegalArgumentException("VIDEO_NOT_FOUND"));

        if (!video.getContent().getId().equals(seriesId)) {
            throw new IllegalArgumentException("MISMATCH: videoId가 해당 seriesId에 속하지 않습니다.");
        }

        if (video.getStatus() != VideoStatus.DRAFT) {
            throw new IllegalArgumentException("INVALID_VIDEO_STATUS: DRAFT 상태에서만 confirm 가능합니다.");
        }

        var stat = safeStat(req.objectKey());
        if (stat.size() <= 0) {
            throw new UploadNotCompletedException();
        }

        video.updateInfo(req.episodeTitle(), req.episodeDescription());
        video.updateStatus(VideoStatus.PRIVATE);

        VideoFile vf = videoFileRepository.findByVideoId(video.getId())
                .orElseThrow(() -> new IllegalStateException("VIDEO_FILE_NOT_FOUND"));

        vf.updateOriginalKey(req.objectKey());
        vf.updateTranscodeStatus(TranscodeStatus.WAITING);

        return new AdminSeriesEpisodeConfirmResponse(
                series.getId(),
                video.getEpisodeNo(),
                video.getId(),
                vf.getId(),
                vf.getOriginalUrl(),
                vf.getTranscodeStatus().name()
        );
    }

    private void validate(Long seriesId, AdminSeriesEpisodeConfirmRequest req) {
        if (seriesId == null) throw new IllegalArgumentException("seriesId는 필수입니다.");
        if (req == null || req.videoId() == null) throw new IllegalArgumentException("videoId는 필수입니다.");
        if (!StringUtils.hasText(req.objectKey())) throw new IllegalArgumentException("objectKey는 필수입니다.");
        if (!StringUtils.hasText(req.episodeTitle())) throw new IllegalArgumentException("episodeTitle은 필수입니다.");
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