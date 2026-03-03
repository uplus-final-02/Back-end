package org.backend.admin.series.service;

import common.enums.ContentAccessLevel;
import common.enums.ContentStatus;
import common.enums.ContentType;
import content.entity.Content;
import content.repository.ContentRepository;
import lombok.RequiredArgsConstructor;
import org.backend.admin.series.dto.AdminSeriesDraftCreateRequest;
import org.backend.admin.series.dto.AdminSeriesDraftCreateResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminSeriesDraftService {

    private final ContentRepository contentRepository;

    @Transactional
    public AdminSeriesDraftCreateResponse createDraft(AdminSeriesDraftCreateRequest req) {
        if (req == null || req.uploaderId() == null) {
            throw new IllegalArgumentException("uploaderId는 필수입니다.");
        }

        Content content = Content.builder()
                .type(ContentType.SERIES)
                .title("TEMP_SERIES_TITLE")
                .description(null)
                .thumbnailUrl("TEMP_THUMBNAIL_URL")
                .status(ContentStatus.HIDDEN)
                .uploaderId(req.uploaderId())
                .accessLevel(ContentAccessLevel.FREE)
                .build();

        Content saved = contentRepository.save(content);
        return new AdminSeriesDraftCreateResponse(saved.getId());
    }
}