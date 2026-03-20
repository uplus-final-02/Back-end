package org.backend.userapi.payment.dto;

import common.enums.PaymentProvider;
import common.enums.PaymentStatus;
import common.enums.PlanType;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDateTime;

@Getter
@Builder
@Jacksonized  // @Builder 클래스를 Jackson이 역직렬화할 수 있도록 (Redis 캐시 복원용)
public class SubscribeResponse {

	// 결제 정보
    private Long paymentId;
    private Integer amount;
    private PaymentStatus paymentStatus;
    private PaymentProvider paymentProvider;

    // 구독 정보
    private Long subscriptionId;
    private PlanType planType; 

    private LocalDateTime expiresAt; // 서비스에서 now+30 계산됨
}
