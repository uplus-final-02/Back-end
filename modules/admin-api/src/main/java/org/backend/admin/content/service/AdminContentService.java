package org.backend.admin.content.service;

import lombok.RequiredArgsConstructor;

import org.backend.admin.content.dto.AdminContentListResponse;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import content.entity.Content;
import content.repository.ContentRepository;

import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminContentService {

	private final ContentRepository contentRepository;

    public Page<AdminContentListResponse> getContents(Pageable pageable) {
        Page<Content> page = contentRepository
                .findAllByOrderByCreatedAtDesc(pageable);

        List<AdminContentListResponse> content = page.getContent().stream()
                .map(c -> new AdminContentListResponse(
                        c.getId(),           
                        c.getTitle(),
                        c.getType(),
                        c.getUploaderId(),
                        c.getStatus()
                ))
                .toList();

        return new PageImpl<>(content, pageable, page.getTotalElements());
    }
}
