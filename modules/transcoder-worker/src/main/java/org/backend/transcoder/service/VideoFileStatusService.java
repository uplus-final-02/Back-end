package org.backend.transcoder.service;

import common.enums.TranscodeStatus;
import content.entity.VideoFile;
import content.repository.VideoFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VideoFileStatusService {

    private final VideoFileRepository videoFileRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessing(Long videoFileId) {
        VideoFile vf = videoFileRepository.findById(videoFileId)
                .orElseThrow(() -> new IllegalStateException("VIDEO_FILE_NOT_FOUND: " + videoFileId));

        // DONE/FAILED면 덮어쓰지 않도록 보호(원하면 제거 가능)
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
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long videoFileId) {
        VideoFile vf = videoFileRepository.findById(videoFileId)
                .orElseThrow(() -> new IllegalStateException("VIDEO_FILE_NOT_FOUND: " + videoFileId));

        vf.updateTranscodeStatus(TranscodeStatus.FAILED);
    }
}