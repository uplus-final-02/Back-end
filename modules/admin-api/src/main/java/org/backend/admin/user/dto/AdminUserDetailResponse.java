package org.backend.admin.user.dto;

import common.enums.AuthProvider;
import common.enums.UserStatus;

import java.time.LocalDateTime;
import java.util.List;

public record AdminUserDetailResponse(
        UserInfo user,
        SubscriptionInfo subscription,
        List<PaymentInfo> paymentHistory
) {

    public record UserInfo(
            Long userId,
            String nickname,
            UserStatus userStatus,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            LocalDateTime deletedAt
    ) {}

    public record SubscriptionInfo(
            Long subscriptionId,
            String planType,
            String subscriptionStatus,
            LocalDateTime startedAt,
            LocalDateTime expiresAt
    ) {}

    public record PaymentInfo(
            Long paymentId,
            Integer amount,
            String paymentStatus,
            String paymentProvider,
            LocalDateTime requestAt,
            LocalDateTime approvedAt
    ) {}
}