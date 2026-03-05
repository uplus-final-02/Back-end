package org.backend.admin.content.service;

import lombok.RequiredArgsConstructor;

import org.backend.admin.content.dto.AdminContentListResponse;
import org.backend.admin.content.dto.AdminContentUpdateRequest;
import org.backend.admin.content.dto.AdminContentUpdateResponse;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import common.entity.Tag;
import common.repository.TagRepository;
import content.entity.Content;
import content.entity.ContentTag;
import content.repository.ContentRepository;

import java.util.*;

@Service
@RequiredArgsConstructor
public class AdminContentService {

	private final ContentRepository contentRepository;
	private final TagRepository tagRepository;

	@Transactional(readOnly = true)
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
    
    @Transactional
    public AdminContentUpdateResponse updateMetadata(Long contentId, AdminContentUpdateRequest req) {
    	Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new IllegalArgumentException("콘텐츠를 찾을 수 없습니다. contentId=" + contentId));
    	
    	content.updateMetadata(
                req.title(),
                req.description(),
                req.thumbnailUrl(),
                req.accessLevel(),
                req.status()
        );
    	
    	List<Long> tagIds = req.tagIds();
        if (tagIds == null || tagIds.isEmpty()) {
            throw new IllegalArgumentException("tagIds는 최소 1개 이상이어야 합니다.");
        }

        List<Tag> tags = tagRepository.findAllById(tagIds);

        if (tags.size() != tagIds.stream().distinct().count()) {
            throw new IllegalArgumentException("존재하지 않는 태그가 포함되어 있습니다.");
        }
        
        content.getContentTags().clear();
        for (Tag tag : tags) {
            content.getContentTags().add(
                    ContentTag.builder()
                            .content(content)
                            .tag(tag)
                            .build()
            );
        }

        
        Content saved = contentRepository.save(content);

        return new AdminContentUpdateResponse(saved.getId(), saved.getStatus(), saved.getAccessLevel());
    }
        
        
    	

}
