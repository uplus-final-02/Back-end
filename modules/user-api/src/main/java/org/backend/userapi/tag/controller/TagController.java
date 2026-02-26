package org.backend.userapi.tag.controller;

import java.util.List;

import common.entity.Tag;
import org.backend.userapi.common.dto.ApiResponse;

import org.backend.userapi.tag.service.TagService;
import org.backend.userapi.user.dto.request.PreferredTagUpdateRequest;
import org.backend.userapi.user.service.UserTagPreferenceService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import core.security.principal.JwtPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class TagController {

    private final UserTagPreferenceService userTagPreferenceService;
    private final TagService tagService;

    // is_active == true 인 태그만 조회 (선호 태그 선택 / 검색 - 태그 선택 / 메인 메뉴 태그 카테고리 등등 ... )
    // URL: http://localhost:8081/api/tags?section=LEVEL_1
    @Operation(summary = "태그 목록 조회", description = "섹션(LEVEL)에 따라 활성화된 태그 목록을 필터링하여 조회합니다.")
    @GetMapping("/api/tags")
    public ApiResponse<List<Tag>> getTags(
            @RequestParam TagSection section
    ) {
        List<Tag> tags = tagService.getTagsByPriorities(section.getTargetPriorities());
        return new ApiResponse<>(200, "태그 목록 조회 성공", tags);
    }

    @lombok.Getter
    @lombok.RequiredArgsConstructor
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