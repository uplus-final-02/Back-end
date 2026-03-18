// modules/user-api/src/main/java/org/backend/userapi/content/publish/service/UserPublishWaitService.java
package org.backend.userapi.content.publish.service;

import content.entity.UserContent;
import content.entity.UserVideoFile;
import content.repository.UserContentRepository;
import content.repository.UserVideoFileRepository;
import lombok.RequiredArgsConstructor;
import org.backend.userapi.content.publish.dto.UserPublishStatusResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserPublishWaitService {

    private final UserContentRepository userContentRepository;
    private final UserVideoFileRepository userVideoFileRepository;

    @Transactional(readOnly = true)
    public UserPublishStatusResponse getStatus(Long userContentId) {
        UserContent uc = userContentRepository.findById(userContentId)
                .orElseThrow(() -> new IllegalArgumentException("USER_CONTENT_NOT_FOUND: " + userContentId));

        UserVideoFile uvf = userVideoFileRepository.findByContent_Id(uc.getId())
                .orElseThrow(() -> new IllegalStateException("USER_VIDEO_FILE_NOT_FOUND: contentId=" + uc.getId()));

        return new UserPublishStatusResponse(
                uc.getId(),
                uc.isPublishRequested(),
                uc.getContentStatus(),
                uvf.getTranscodeStatus()
        );
    }
}