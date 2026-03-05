package org.backend.admin.content.service;

import lombok.RequiredArgsConstructor;

import org.backend.admin.content.dto.AdminContentDetailResponse;
import org.backend.admin.content.dto.AdminContentListResponse;
import org.backend.admin.content.dto.AdminContentUpdateRequest;
import org.backend.admin.content.dto.AdminContentUpdateResponse;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import common.entity.Tag;
import common.enums.ContentType;
import common.repository.TagRepository;
import content.entity.Content;
import content.entity.ContentTag;
import content.entity.Video;
import content.repository.ContentRepository;
import content.repository.ContentTagRepository;
import content.repository.VideoRepository;

import java.util.*;

@Service
@RequiredArgsConstructor
public class AdminContentService {

	private final ContentRepository contentRepository;
	private final TagRepository tagRepository;
	private final ContentTagRepository contentTagRepository;
    private final VideoRepository videoRepository;
    
    
    // 콘텐츠 목록 조회
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
    
	// 콘텐츠 메타데이터 업데이트
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

        List<Long> uniqueTagIds = tagIds.stream().distinct().toList();
        List<Tag> tags = tagRepository.findAllById(uniqueTagIds);

        if (tags.size() != tagIds.stream().distinct().count()) {
            throw new IllegalArgumentException("존재하지 않는 태그가 포함되어 있습니다.");
        }
        
        contentTagRepository.deleteAllByContentId(contentId);
        
        content.getContentTags().clear();
        
        for (Tag tag : tags) {
            content.getContentTags().add(
            		ContentTag.builder().content(content).tag(tag).build());
        }
        
        AdminContentUpdateRequest.EpisodeUpdate ep = req.episode();
        if (ep != null) {

            if (content.getType() == ContentType.SINGLE) {
                throw new IllegalArgumentException("단건 콘텐츠는 비디오 메타 수정이 허용되지 않습니다.");
            }

            Video video = videoRepository.findById(ep.videoId())
                    .orElseThrow(() -> new IllegalArgumentException("비디오를 찾을 수 없습니다. videoId=" + ep.videoId()));

            Long parentId = video.getContent().getId();
            if (!parentId.equals(contentId)) {
                throw new IllegalArgumentException("해당 콘텐츠에 속한 에피소드가 아닙니다. contentId=" + contentId + ", videoId=" + ep.videoId());
            }
            
            String newTitle = (ep.title() != null) ? ep.title() : video.getTitle();
            String newDesc  = (ep.description() != null) ? ep.description() : video.getDescription();
            video.updateInfo(newTitle, newDesc);
        }
        
        Content saved = contentRepository.save(content);

        return new AdminContentUpdateResponse(saved.getId(), saved.getStatus(), saved.getAccessLevel());
    }
        
       
    // 콘텐츠 상세 조회
    public AdminContentDetailResponse getContentDetail(Long contentId) {

        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new IllegalArgumentException("콘텐츠를 찾을 수 없습니다. contentId=" + contentId));

        // tags
        List<AdminContentDetailResponse.TagItem> tags = contentTagRepository
                .findActiveTagsByContentId(contentId)
                .stream()
                .map(t -> new AdminContentDetailResponse.TagItem(t.getTagId(), t.getName()))
                .toList();

        // episodes (SERIES만)
        List<AdminContentDetailResponse.EpisodeItem> episodes =
                (content.getType() == ContentType.SERIES)
                        ? videoRepository.findAdminEpisodesByContentId(contentId).stream()
                            .map(v -> new AdminContentDetailResponse.EpisodeItem(
                                    v.getVideoId(),
                                    v.getEpisodeNo(),
                                    v.getTitle(),
                                    v.getDescription()
                            ))
                            .toList()
                        : List.of();

        return new AdminContentDetailResponse(
                content.getId(),
                content.getType(),
                content.getTitle(),
                content.getDescription(),
                content.getThumbnailUrl(),
                content.getStatus(),
                content.getAccessLevel(),
                content.getUploaderId(),
                content.getCreatedAt(),
                content.getUpdatedAt(),
                tags,
                episodes
        );
    }
    	

}
