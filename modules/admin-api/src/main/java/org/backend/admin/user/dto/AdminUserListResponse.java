package org.backend.admin.user.dto;

import common.enums.AuthProvider;

import java.time.LocalDateTime;
import java.util.List;

public record AdminUserListResponse(
        Long userId,
        String name,
        LocalDateTime createdAt,
        List<LoginMethod> loginMethods
) {
    public record LoginMethod(
            AuthProvider authProvider,
            String identifier
    ) {}
}