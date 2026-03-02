package org.backend.userapi.payment.service;

import common.enums.PaymentProvider;
import common.enums.PaymentStatus;
import common.enums.PlanType;
import common.enums.SubscriptionStatus;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.backend.userapi.payment.dto.SubscribeRequest;
import org.backend.userapi.payment.dto.SubscribeResponse;
import org.springframework.stereotype.Service;

import user.entity.Payment;
import user.entity.Subscriptions;
import user.entity.User;
import user.repository.PaymentRepository;
import user.repository.SubscriptionsRepository;
import user.repository.UserRepository;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final int SUBSCRIPTION_AMOUNT = 4900;
    private static final int SUBSCRIPTION_DAYS = 30;

    private final UserRepository userRepository;
    private final SubscriptionsRepository subscriptionsRepository;
    private final PaymentRepository paymentRepository;

    @Transactional
    public SubscribeResponse subscribe(Long userId, SubscribeRequest request) {
        LocalDateTime now = LocalDateTime.now();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. userId=" + userId));

        PaymentProvider paymentProvider = request.getPaymentProvider();

        Subscriptions subscription = subscriptionsRepository.findByUser_Id(userId).orElse(null);

        if (subscription != null) {

        	boolean notExpired = now.isBefore(subscription.getExpiresAt());
            SubscriptionStatus status = subscription.getSubscriptionStatus();

            // 이미 구독 중
            if (status == SubscriptionStatus.ACTIVE && notExpired) {
                throw new IllegalStateException("이미 구독 중입니다.");
            }

            // 해지 예약 상태(만료 전)
            if (status == SubscriptionStatus.CANCELED && notExpired) {
                throw new IllegalArgumentException("해지 예약 상태입니다. 만료 후 재구독 가능합니다.");
            }

            // 만료
            if (!notExpired && status != SubscriptionStatus.EXPIRED) {
            	subscription.expire();
            }
        }
        
        LocalDateTime newExpiresAt = now.plusDays(SUBSCRIPTION_DAYS);
        
        // 구독 upsert
        if (subscription == null) {
            subscription = Subscriptions.builder()
                    .user(user)
                    .planType(PlanType.SUB_BASIC)
                    .subscriptionStatus(SubscriptionStatus.ACTIVE)
                    .startedAt(now)
                    .expiresAt(newExpiresAt)
                    .build();
        } else {
        	subscription.restart(now, newExpiresAt);
        }

        subscription = subscriptionsRepository.save(subscription);

        // 결제 이력 생성(Mock)
        Payment payment = Payment.builder()
                .subscription(subscription)
                .user(user)
                .amount(SUBSCRIPTION_AMOUNT)
                .paymentStatus(PaymentStatus.SUCCEEDED)
                .paymentProvider(paymentProvider)
                .requestAt(now)
                .approvedAt(now)
                .build();

        payment = paymentRepository.save(payment);

        return SubscribeResponse.builder()
                .paymentId(payment.getId())
                .amount(payment.getAmount())
                .paymentStatus(payment.getPaymentStatus())
                .paymentProvider(payment.getPaymentProvider())
                .planType(subscription.getPlanType())          // SUB_BASIC
                .subscriptionId(subscription.getId())
                .expiresAt(subscription.getExpiresAt())
                .build();
    }
}