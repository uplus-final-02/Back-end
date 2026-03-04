package org.backend.userapi.membership.dto;

import common.enums.PlanType;
import common.enums.SubscriptionStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class SubscriptionMeResponse {

    private Long subscriptionId;
    private PlanType grade; // plan_type

    private SubscriptionStatus subscriptionStatus; // DB 값 그대로
    private String displayStatus;                 // 계산값(EXPIRED 포함)

    private LocalDateTime startedAt;
    private LocalDateTime expiresAt;

    private boolean paid; // 계산값
}
