package org.backend.admin.content.controller;

import org.backend.admin.common.dto.AdminApiResponse;
import org.backend.admin.content.dto.AdminContentDeleteResponse;
import org.backend.admin.content.dto.AdminContentDetailResponse;
import org.backend.admin.content.dto.AdminContentListResponse;
import org.backend.admin.content.dto.AdminContentUpdateRequest;
import org.backend.admin.content.dto.AdminContentUpdateResponse;
import org.backend.admin.content.dto.AdminThumbnailUploadResponse;
import org.backend.admin.content.service.AdminContentService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;

import common.enums.ContentStatus;
import lombok.RequiredArgsConstructor;


@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/contents")
public class AdminContentController {

    private final AdminContentService adminContentService;

    @GetMapping("/list")
    public AdminApiResponse<Page<AdminContentListResponse>> getContents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "LATEST") String sort,
            @RequestParam(required = false) ContentStatus status
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<AdminContentListResponse> result =
                adminContentService.getContents(pageable, sort, status);
        return AdminApiResponse.ok("조회 성공", result);
    }
    
    @PutMapping("/{contentId}/metadata")
    public AdminContentUpdateResponse updateContentMetadata(
            @PathVariable Long contentId,
            @RequestBody AdminContentUpdateRequest request
    ) {
        return adminContentService.updateMetadata(contentId, request);
    }
    
    @GetMapping("/{contentId}")
    public AdminApiResponse<AdminContentDetailResponse> getContentDetail(@PathVariable Long contentId) {
        AdminContentDetailResponse result = adminContentService.getContentDetail(contentId);
        return AdminApiResponse.ok("조회 성공", result);
    }
    
    @DeleteMapping("/{contentId}")
    public AdminApiResponse<AdminContentDeleteResponse> deleteContent(
            @PathVariable Long contentId
    ) {
        AdminContentDeleteResponse response = adminContentService.deleteContent(contentId);
        return AdminApiResponse.ok("삭제 완료", response);
    }
    
    // 썸네일 업로드
    @PostMapping(value = "/{contentId}/thumbnail", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AdminApiResponse<AdminThumbnailUploadResponse> uploadThumbnail(
            @PathVariable Long contentId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) Long videoId
    ) {
        AdminThumbnailUploadResponse response =
                adminContentService.uploadThumbnail(contentId, videoId, file);

        return AdminApiResponse.ok("썸네일 업로드 성공", response);
    }
}
