package org.backend.transcoder.service;

import common.enums.ContentStatus;
import common.enums.TranscodeStatus;
import content.entity.Content;
import content.entity.VideoFile;
import content.repository.ContentRepository;
import content.repository.VideoFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VideoFileStatusService {

    private final VideoFileRepository videoFileRepository;
    private final ContentRepository contentRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessing(Long videoFileId) {
        VideoFile vf = videoFileRepository.findById(videoFileId)
                .orElseThrow(() -> new IllegalStateException("VIDEO_FILE_NOT_FOUND: " + videoFileId));

        if (vf.getTranscodeStatus() == TranscodeStatus.DONE || vf.getTranscodeStatus() == TranscodeStatus.FAILED) {
            return;
        }
        vf.updateTranscodeStatus(TranscodeStatus.PROCESSING);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markDone(Long videoFileId, String hlsMasterKey, int durationSec) {
        VideoFile vf = videoFileRepository.findById(videoFileId)
                .orElseThrow(() -> new IllegalStateException("VIDEO_FILE_NOT_FOUND: " + videoFileId));

        vf.updateHlsKey(hlsMasterKey);
        vf.updateDurationSec(durationSec);
        vf.updateTranscodeStatus(TranscodeStatus.DONE);

        Long contentId = vf.getVideo().getContent().getId();
        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new IllegalStateException("CONTENT_NOT_FOUND: " + contentId));

        if (!content.isPublishRequested()) {
            return;
        }

        boolean anyDone = videoFileRepository.existsByVideo_Content_IdAndTranscodeStatus(contentId, TranscodeStatus.DONE);

        if (anyDone && content.getStatus() != ContentStatus.ACTIVE) {
            content.activate();
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long videoFileId) {
        VideoFile vf = videoFileRepository.findById(videoFileId)
                .orElseThrow(() -> new IllegalStateException("VIDEO_FILE_NOT_FOUND: " + videoFileId));

        vf.updateTranscodeStatus(TranscodeStatus.FAILED);
    }
}