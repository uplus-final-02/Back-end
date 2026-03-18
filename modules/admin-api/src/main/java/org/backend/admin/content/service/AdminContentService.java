package org.backend.admin.content.service;

import common.enums.TranscodeStatus;
import content.repository.VideoFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.backend.admin.content.dto.AdminContentDeleteResponse;
import org.backend.admin.content.dto.AdminContentDetailResponse;
import org.backend.admin.content.dto.AdminContentListResponse;
import org.backend.admin.content.dto.AdminContentUpdateRequest;
import org.backend.admin.content.dto.AdminContentUpdateResponse;
import org.backend.admin.content.dto.AdminThumbnailUploadResponse;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import common.entity.Tag;
import common.enums.ContentStatus;
import common.enums.ContentType;
import common.repository.TagRepository;
import content.entity.Content;
import content.entity.ContentTag;
import content.entity.Video;
import content.repository.ContentRepository;
import content.repository.ContentTagRepository;
import content.repository.VideoRepository;
import core.storage.ObjectStorageService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminContentService {

	private final ContentRepository contentRepository;
	private final TagRepository tagRepository;
	private final ContentTagRepository contentTagRepository;
    private final VideoRepository videoRepository;
    private final ObjectStorageService objectStorageService;
    private final VideoFileRepository videoFileRepository;
    
    
    // 콘텐츠 목록 조회
	@Transactional(readOnly = true)
    public Page<AdminContentListResponse> getContents(Pageable pageable,String sort, ContentStatus status) {
		
		Sort sortOption = "OLDEST".equalsIgnoreCase(sort)
	            ? Sort.by(Sort.Direction.ASC, "createdAt")
	            : Sort.by(Sort.Direction.DESC, "createdAt");

	    Pageable sortedPageable = PageRequest.of(
	            pageable.getPageNumber(),
	            pageable.getPageSize(),
	            sortOption
	    );
	    
		Page<Content> page = (status == null)
	            ? contentRepository.findAll(sortedPageable)
	            : contentRepository.findByStatus(status, sortedPageable);

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
    	log.info("[MARKER] updateMetadata v=20260305-1432");
    	Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new IllegalArgumentException("콘텐츠를 찾을 수 없습니다. contentId=" + contentId));
    	
    	content.updateMetadata(
                req.title(),
                req.description(),
                req.thumbnailUrl(),
                req.accessLevel(),
                null
        );

        if (req.status() != null) {
            if (req.status() == ContentStatus.ACTIVE) {
                content.requestPublish();

                boolean anyDone = videoFileRepository
                        .existsByVideo_Content_IdAndTranscodeStatus(contentId, TranscodeStatus.DONE);

                if (anyDone) {
                    content.activate();
                }

            } else if (req.status() == ContentStatus.HIDDEN) {
                content.cancelPublishRequest();
                content.hide();
            }
        }


        List<Long> tagIds = req.tagIds();
    	if (tagIds != null) {
            List<Long> uniqueTagIds = tagIds.stream().distinct().toList();
            if (uniqueTagIds.isEmpty()) {
                throw new IllegalArgumentException("tagIds는 최소 1개 이상이어야 합니다.");
            }

            List<Tag> tags = tagRepository.findAllById(uniqueTagIds);
            if (tags.size() != uniqueTagIds.size()) {
                throw new IllegalArgumentException("존재하지 않는 태그가 포함되어 있습니다.");
            }

            List<ContentTag> current = content.getContentTags();
            Set<Long> currentTagIds = current.stream().map(ct -> ct.getTag().getId()).collect(java.util.stream.Collectors.toSet());

            Set<Long> target = new java.util.HashSet<>(uniqueTagIds);

            // 기존 매핑 제거
            current.removeIf(ct -> !target.contains(ct.getTag().getId())); 

            // 기존에 없던 것만 add
            Map<Long, Tag> tagMap = tags.stream().collect(java.util.stream.Collectors.toMap(Tag::getId, t -> t));
            for (Long tid : target) {
                if (!currentTagIds.contains(tid)) {
                    current.add(ContentTag.builder().content(content).tag(tagMap.get(tid)).build());
                }
            }
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
            
            log.info("video before title={}, desc={}", video.getTitle(), video.getDescription());
            video.updateInfo(newTitle, newDesc);
            log.info("video after  title={}, desc={}", video.getTitle(), video.getDescription());
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
    
    // 콘텐츠 삭제
    @Transactional
    public AdminContentDeleteResponse deleteContent(Long contentId) {
    	Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new IllegalArgumentException("콘텐츠를 찾을 수 없습니다. contentId=" + contentId));

    	if (content.getStatus() == ContentStatus.DELETED) {
    	    return AdminContentDeleteResponse.from(content);
    	}
    	content.delete();
    	
    	return AdminContentDeleteResponse.from(content);
    	
    	
    	
    }
    	
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".png", ".jpg", ".jpeg", ".webp");

    public AdminThumbnailUploadResponse uploadThumbnail(Long contentId, Long videoId, MultipartFile file) {
        validateFile(file);

        String extension = resolveExtension(file.getOriginalFilename());
        String contentType = resolveContentType(file.getContentType(), extension);
        String objectPath = buildThumbnailPath(contentId, videoId, extension);
        String uploadedThumbnailUrl = uploadAndBuildUrl(file, objectPath, extension, contentType);

        return AdminThumbnailUploadResponse.builder()
                .uploadedThumbnailUrl(uploadedThumbnailUrl)
                .build();
    }

    private void validateFile(MultipartFile file) {
    	System.out.println("originalFilename = " + file.getOriginalFilename());
        System.out.println("contentType = " + file.getContentType());
        System.out.println("size = " + file.getSize());
        
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드할 썸네일 파일이 비어 있습니다.");
        }

        String originalFilename = file.getOriginalFilename();
        String extension = resolveExtension(originalFilename);

        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("png, jpg, jpeg, webp 이미지 파일만 업로드 가능합니다.");
        }

        String contentType = file.getContentType();
        if (contentType != null && !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("이미지 파일만 업로드 가능합니다.");
        }
    }

    private String resolveExtension(String originalFilename) {
        if (originalFilename == null || !originalFilename.contains(".")) {
            throw new IllegalArgumentException("파일 확장자를 확인할 수 없습니다.");
        }

        String extension = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();

        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("지원하지 않는 썸네일 형식입니다.");
        }

        return extension;
    }

    private String resolveContentType(String contentType, String extension) {
        if (contentType != null && contentType.startsWith("image/")) {
            return contentType;
        }

        return switch (extension) {
            case ".png" -> "image/png";
            case ".jpg", ".jpeg" -> "image/jpeg";
            case ".webp" -> "image/webp";
            default -> throw new IllegalArgumentException("지원하지 않는 썸네일 형식입니다.");
        };
    }

    private String buildThumbnailPath(Long contentId, Long videoId, String extension) {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String uuid = UUID.randomUUID().toString();

        return (videoId == null)
                ? "images/thumbnails/%s/%d/%s%s".formatted(date, contentId, uuid, extension)
                : "images/thumbnails/%s/%d/%d/%s%s".formatted(date, contentId, videoId, uuid, extension);
    }

    private String uploadAndBuildUrl(MultipartFile file, String objectPath, String extension, String contentType) {
        Path tempFile = null;

        try {
            tempFile = Files.createTempFile("thumbnail-", extension);
            file.transferTo(tempFile.toFile());

            objectStorageService.uploadFromFile(objectPath, tempFile, contentType);
            return objectStorageService.buildPublicUrl(objectPath);

        } catch (IOException e) {
            throw new IllegalStateException("썸네일 파일 처리 중 오류가 발생했습니다.", e);
        } catch (Exception e) {
            throw new IllegalStateException("썸네일 업로드에 실패했습니다.", e);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                }
            }
        }
    }
}