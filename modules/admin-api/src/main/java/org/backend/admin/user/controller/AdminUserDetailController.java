package org.backend.admin.user.controller;

import core.security.principal.JwtPrincipal;
import lombok.RequiredArgsConstructor;
import org.backend.admin.user.dto.AdminUserDetailResponse;
import org.backend.admin.user.service.AdminUserDetailService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/users")
public class AdminUserDetailController {

    private final AdminUserDetailService adminUserDetailService;

    @GetMapping("/{userId}")
    public AdminUserDetailResponse getUserDetail(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long userId
    ) {
        if (principal == null) {
            throw new IllegalStateException("UNAUTHORIZED: JwtPrincipal is null");
        }
        return adminUserDetailService.getUserDetail(userId);
    }
}