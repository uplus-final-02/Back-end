package org.backend.userapi.auth.service;

import lombok.RequiredArgsConstructor;
import user.entity.Subscriptions;
import user.entity.UserUplusVerified;
import user.repository.SubscriptionsRepository;
import user.repository.UserUplusVerifiedRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import common.enums.SubscriptionStatus;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MembershipCheckService {
	
	private final SubscriptionsRepository subscriptionsRepository;
    private final UserUplusVerifiedRepository userUplusVerifiedRepository;

    @Transactional(readOnly = true)
    public boolean isPaid(Long userId) {
        Subscriptions sub = subscriptionsRepository.findByUser_Id(userId).orElse(null);
        if (sub == null) return false;

        LocalDateTime now = LocalDateTime.now();
        boolean notExpired = sub.getExpiresAt() != null && sub.getExpiresAt().isAfter(now);

        SubscriptionStatus status = sub.getSubscriptionStatus();
        return notExpired && (status == SubscriptionStatus.ACTIVE || status == SubscriptionStatus.CANCELED);
    }

    @Transactional(readOnly = true)
    public boolean isUplus(Long userId) {
        UserUplusVerified verified = userUplusVerifiedRepository.findByUser_Id(userId).orElse(null);
        return verified != null && verified.isVerified();
    }
}
