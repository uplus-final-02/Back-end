package org.backend.userapi.content.publish.service;

import common.enums.ContentStatus;
import common.enums.TranscodeStatus;
import content.entity.UserContent;
import content.entity.UserVideoFile;
import content.repository.UserVideoFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserContentPublishPolicyService {

    private final UserVideoFileRepository userVideoFileRepository;

    @Transactional
    public ContentStatus applyPolicy(UserContent uc) {
        if (uc.getContentStatus() == ContentStatus.DELETED) {
            return uc.getContentStatus();
        }

        if (!uc.isPublishRequested()) {
            return uc.getContentStatus();
        }

        UserVideoFile uvf = userVideoFileRepository.findByContent_Id(uc.getId())
                .orElse(null);

        TranscodeStatus ts = (uvf != null) ? uvf.getTranscodeStatus() : null;

        ContentStatus desired = uc.getContentStatus();

        if (desired != ContentStatus.ACTIVE) {
            uc.hide();
            return uc.getContentStatus();
        }

        if (ts == TranscodeStatus.DONE) {
            uc.activate();
        } else {
            uc.hide();
        }

        return uc.getContentStatus();
    }
}