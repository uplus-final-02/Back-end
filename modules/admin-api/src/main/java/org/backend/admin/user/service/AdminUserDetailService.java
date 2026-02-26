package org.backend.admin.user.service;

import lombok.RequiredArgsConstructor;
import org.backend.admin.user.dto.AdminUserDetailResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import user.entity.Payment;
import user.entity.Subscriptions;
import user.entity.User;
import user.repository.PaymentRepository;
import user.repository.SubscriptionsRepository;
import user.repository.UserRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserDetailService {

    private final UserRepository userRepository;
    private final SubscriptionsRepository subscriptionsRepository;
    private final PaymentRepository paymentRepository;

    public AdminUserDetailResponse getUserDetail(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("USER_NOT_FOUND"));

        Subscriptions sub = subscriptionsRepository.findByUser_Id(userId).orElse(null);
        List<Payment> payments = paymentRepository.findByUserIdOrderByRequestAtDesc(userId);

        AdminUserDetailResponse.SubscriptionInfo subscriptionInfo =
                (sub == null) ? null : new AdminUserDetailResponse.SubscriptionInfo(
                        sub.getId(),
                        sub.getPlanType(),
                        sub.getSubscriptionStatus(),
                        sub.getStartedAt(),
                        sub.getExpiresAt()
                );

        List<AdminUserDetailResponse.PaymentInfo> paymentInfos = payments.stream()
                .map(p -> new AdminUserDetailResponse.PaymentInfo(
                        p.getId(),
                        p.getAmount(),
                        p.getPaymentStatus(),
                        p.getPaymentProvider(),
                        p.getRequestAt(),
                        p.getApprovedAt()
                ))
                .toList();

        return new AdminUserDetailResponse(
                new AdminUserDetailResponse.UserInfo(
                        user.getId(),
                        user.getNickname(),
                        user.getUserStatus(),
                        user.getCreatedAt(),
                        user.getUpdatedAt(),
                        user.getDeletedAt()
                ),
                subscriptionInfo,
                paymentInfos
        );
    }
}