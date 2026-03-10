package org.backend.admin.tag.controller;


import lombok.RequiredArgsConstructor;
import org.backend.admin.common.dto.AdminApiResponse;
import org.backend.admin.tag.dto.TagResponse;
import org.backend.admin.tag.service.TagService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    // is_active == true 인 태그만 조회 (선호 태그 선택 / 검색 - 태그 선택 / 메인 메뉴 태그 카테고리 등등 ... )
    // URL: http://localhost:8082/api/admin/tags
    @GetMapping("/api/admin/tags")
    public AdminApiResponse<List<TagResponse>> getTags(
            @RequestParam(defaultValue = "LEVEL_2") TagSection section
    ) {
        List<TagResponse> tags = tagService.getTagsByPriorities(section.getTargetPriorities());
        return AdminApiResponse.ok("태그 목록 조회 성공", tags);
    }

    @lombok.Getter
    @RequiredArgsConstructor
    public enum TagSection {
        // 0:USER
        // 1:ADMIN-MAIN
        // 2:ADMIN-NOT MAIN

        // 1이면 priority == 1
        LEVEL_1(1, List.of(1L)),

        // 2라면 1, 2 조회
        LEVEL_2(2, List.of(1L, 2L)),

        // 0이라면 0, 1, 2 조회
        LEVEL_0(0, List.of(0L, 1L, 2L));

        private final int code;
        private final List<Long> targetPriorities;
    }
}