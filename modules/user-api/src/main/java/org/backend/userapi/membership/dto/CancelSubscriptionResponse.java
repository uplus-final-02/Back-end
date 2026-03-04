package org.backend.userapi.membership.dto;

import common.enums.PlanType;
import common.enums.SubscriptionStatus;
import lombok.Builder;
import lombok.Getter;


import java.time.LocalDateTime;

@Getter
@Builder
public class CancelSubscriptionResponse {

    private Long subscriptionId;
    private PlanType grade; // plan_type

    private SubscriptionStatus subscriptionStatus; // CANCELED
    private LocalDateTime expiresAt;               // 유지

    private boolean paid; // 만료 전이면 true
}