package org.backend.admin.content.controller;

import org.backend.admin.common.dto.AdminApiResponse;
import org.backend.admin.content.dto.AdminContentDeleteResponse;
import org.backend.admin.content.dto.AdminContentDetailResponse;
import org.backend.admin.content.dto.AdminContentListResponse;
import org.backend.admin.content.dto.AdminContentUpdateRequest;
import org.backend.admin.content.dto.AdminContentUpdateResponse;
import org.backend.admin.content.service.AdminContentService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;


@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/contents")
public class AdminContentController {

    private final AdminContentService adminContentService;

    @GetMapping("/list")
    public AdminApiResponse<Page<AdminContentListResponse>> getContents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<AdminContentListResponse> result = adminContentService.getContents(pageable);
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
}
