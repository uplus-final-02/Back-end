package org.backend.transcoder.service;

import common.enums.TranscodeStatus;
import content.entity.UserVideoFile;
import content.repository.UserVideoFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserVideoFileStatusService {

    private final UserVideoFileRepository userVideoFileRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessing(Long userVideoFileId) {
        UserVideoFile uvf = userVideoFileRepository.findById(userVideoFileId)
                .orElseThrow(() -> new IllegalStateException("USER_VIDEO_FILE_NOT_FOUND: " + userVideoFileId));

        if (uvf.getTranscodeStatus() == TranscodeStatus.DONE || uvf.getTranscodeStatus() == TranscodeStatus.FAILED) {
            return;
        }
        uvf.updateTranscodeStatus(TranscodeStatus.PROCESSING);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markDone(Long userVideoFileId, String hlsMasterKey, int durationSec) {
        UserVideoFile uvf = userVideoFileRepository.findById(userVideoFileId)
                .orElseThrow(() -> new IllegalStateException("USER_VIDEO_FILE_NOT_FOUND: " + userVideoFileId));

        uvf.updateHlsKey(hlsMasterKey);
        uvf.updateDurationSec(durationSec);
        uvf.updateTranscodeStatus(TranscodeStatus.DONE);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long userVideoFileId) {
        UserVideoFile uvf = userVideoFileRepository.findById(userVideoFileId)
                .orElseThrow(() -> new IllegalStateException("USER_VIDEO_FILE_NOT_FOUND: " + userVideoFileId));

        uvf.updateTranscodeStatus(TranscodeStatus.FAILED);
    }
}