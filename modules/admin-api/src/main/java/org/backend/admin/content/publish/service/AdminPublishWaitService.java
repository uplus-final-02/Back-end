package org.backend.admin.content.publish.service;

import common.enums.TranscodeStatus;
import content.entity.Content;
import content.repository.ContentRepository;
import content.repository.VideoFileRepository;
import lombok.RequiredArgsConstructor;
import org.backend.admin.content.publish.dto.AdminPublishStatusResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminPublishWaitService {

    private final ContentRepository contentRepository;
    private final VideoFileRepository videoFileRepository;

    @Transactional(readOnly = true)
    public AdminPublishStatusResponse getStatus(Long contentId) {
        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new IllegalArgumentException("CONTENT_NOT_FOUND"));

        boolean anyDone = videoFileRepository.existsByVideo_Content_IdAndTranscodeStatus(contentId, TranscodeStatus.DONE);

        return new AdminPublishStatusResponse(
                content.getId(),
                content.getStatus().name(),
                content.isPublishRequested(),
                anyDone
        );
    }
}