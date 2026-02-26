package org.backend.admin.user.dto;

import common.enums.AuthProvider;

import java.time.LocalDateTime;
import java.util.List;

public record AdminUserListResponse(
        Long userId,                 // 유저 PK
        String name,                 // nickname
        LocalDateTime createdAt,     // 가입일 (User.createdAt)
        List<LoginMethod> loginMethods
) {
    public record LoginMethod(
            AuthProvider authProvider,
            String identifier          // email 있으면 email, 없으면 authProviderSubject
    ) {}
}