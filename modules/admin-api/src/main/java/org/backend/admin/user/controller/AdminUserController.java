package org.backend.admin.user.controller;

import lombok.RequiredArgsConstructor;
import org.backend.admin.user.dto.AdminUserListResponse;
import org.backend.admin.user.service.AdminUserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    public Page<AdminUserListResponse> getUsers(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        // 정렬은 repository JPQL에서 u.createdAt desc로 고정했기 때문에 pageable sort는 무시되어도 됩니다.
        return adminUserService.getUsers(search, pageable);
    }
}