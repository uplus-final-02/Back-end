package org.backend.admin.user.controller;

import lombok.RequiredArgsConstructor;
import org.backend.admin.user.dto.AdminUserDetailResponse;
import org.backend.admin.user.service.AdminUserDetailService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/users")
public class AdminUserDetailController {

    private final AdminUserDetailService adminUserDetailService;

    @GetMapping("/{userId}")
    public AdminUserDetailResponse getUserDetail(@PathVariable Long userId) {
        return adminUserDetailService.getUserDetail(userId);
    }
}