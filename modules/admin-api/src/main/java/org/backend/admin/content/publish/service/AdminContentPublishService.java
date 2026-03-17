package org.backend.admin.content.publish.service;

import content.entity.Content;
import content.repository.ContentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminContentPublishService {

    private final ContentRepository contentRepository;

    @Transactional
    public void requestPublish(Long contentId) {
        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new IllegalArgumentException("CONTENT_NOT_FOUND"));
        content.requestPublish();
    }

    @Transactional
    public void cancelPublish(Long contentId) {
        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new IllegalArgumentException("CONTENT_NOT_FOUND"));
        content.cancelPublishRequest();
    }
}