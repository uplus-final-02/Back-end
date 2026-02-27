package org.backend.admin.user.dto;

import common.enums.*;

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
            PlanType planType,
            SubscriptionStatus subscriptionStatus,
            LocalDateTime startedAt,
            LocalDateTime expiresAt
    ) {}

    public record PaymentInfo(
            Long paymentId,
            Integer amount,
            PaymentStatus paymentStatus,
            PaymentMethod paymentMethod,
            LocalDateTime requestAt,
            LocalDateTime approvedAt
    ) {}
}