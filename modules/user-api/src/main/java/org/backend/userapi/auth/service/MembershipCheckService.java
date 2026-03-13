package org.backend.userapi.auth.service;

import lombok.RequiredArgsConstructor;
import user.entity.Subscriptions;
import user.entity.UserUplusVerified;
import user.repository.SubscriptionsRepository;
import user.repository.TelecomMemberRepository;
import user.repository.UserUplusVerifiedRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import common.enums.SubscriptionStatus;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MembershipCheckService {
	
	private final SubscriptionsRepository subscriptionsRepository;
    private final UserUplusVerifiedRepository userUplusVerifiedRepository;
    private final TelecomMemberRepository telecomMemberRepository;

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public boolean isPaid(Long userId) {
        Subscriptions sub = subscriptionsRepository.findByUser_Id(userId).orElse(null);
        if (sub == null) return false;
        
        LocalDateTime now = LocalDateTime.now();
        boolean notExpired = sub.getExpiresAt() != null && sub.getExpiresAt().isAfter(now);

        SubscriptionStatus status = sub.getSubscriptionStatus();
        return notExpired && (status == SubscriptionStatus.ACTIVE || status == SubscriptionStatus.CANCELED);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean isUplus(Long userId) {
        UserUplusVerified verified = userUplusVerifiedRepository.findByUser_Id(userId).orElse(null);
        
        if (verified == null || !verified.isVerified()) {
            return false;
        }
        
        boolean activeTelecomMember = telecomMemberRepository
                .existsByPhoneNumberAndStatus(verified.getPhoneNumber(), "ACTIVE");
        
        // 통신사 회원 탈퇴 시
        if (!activeTelecomMember) {
            verified.revoke(LocalDateTime.now()); 
            return false;
        }
        
        return true;
    }
}
