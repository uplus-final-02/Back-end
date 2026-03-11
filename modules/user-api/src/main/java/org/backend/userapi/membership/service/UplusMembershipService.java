package org.backend.userapi.membership.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.backend.userapi.common.exception.ConflictException;
import org.backend.userapi.membership.dto.CancelSubscriptionResponse;
import org.backend.userapi.membership.dto.SubscriptionMeResponse;
import org.backend.userapi.membership.dto.UplusVerificationRequest;
import org.backend.userapi.membership.dto.UplusVerificationResponse;
import org.backend.userapi.membership.exception.UplusUserNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import common.enums.SubscriptionStatus;
import user.entity.Subscriptions;
import user.entity.User;
import user.entity.UserUplusVerified;
import user.repository.SubscriptionsRepository;
import user.repository.TelecomMemberRepository;
import user.repository.UserRepository;
import user.repository.UserUplusVerifiedRepository;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UplusMembershipService {
	
	private final UserRepository userRepository;
    private final UserUplusVerifiedRepository userUplusVerifiedRepository;
    private final TelecomMemberRepository telecomMemberRepository;
    private final SubscriptionsRepository subscriptionsRepository;
    
    public UplusVerificationResponse verify(Long userId, UplusVerificationRequest request) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        
        String phoneNumber = normalize(request.getPhoneNumber());
        LocalDateTime now = LocalDateTime.now();
        
        boolean isUplusMember = telecomMemberRepository
                .existsByPhoneNumberAndStatus(phoneNumber, "ACTIVE");
        
        if (!isUplusMember) {
            throw new UplusUserNotFoundException("LG U+ 회원이 아닙니다.");
        }
        
        UserUplusVerified verified = userUplusVerifiedRepository.findByUser_Id(userId).orElse(null);
        // 이미 본인 계정이 같은 번호로 인증된 상태면 재인증 막음
        if (verified != null && verified.isVerified() && phoneNumber.equals(verified.getPhoneNumber())) {
            throw new IllegalArgumentException("이미 인증된 회원입니다.");
        }
        
        // 다른 계정이 이미 사용 중인 번호인지 확인
        UserUplusVerified existingByPhone =
                userUplusVerifiedRepository.findByPhoneNumber(phoneNumber).orElse(null);

        if (existingByPhone != null) {
            Long existingUserId = existingByPhone.getUser().getId();
            if (!existingUserId.equals(userId)) {
                throw new ConflictException("이미 다른 계정에서 인증된 전화번호입니다.");
            }
        }
        
        // 내 계정의 다른 번호였거나, 아예 없던 경우면 재인증 허용
        if (verified == null) {
            verified = UserUplusVerified.createVerified(user, phoneNumber, now);
            userUplusVerifiedRepository.save(verified); 
        } else {
            verified.verify(phoneNumber, now); 
        }
        
        try {
            userUplusVerifiedRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("이미 다른 계정에서 인증된 전화번호입니다.");
        }

        return UplusVerificationResponse.builder()
                .isVerified(true)
                .phoneNumber(phoneNumber)
                .verifiedAt(verified.getVerifiedAt())
                .build();
    }
    
    private String normalize(String phoneNumber) {
        return phoneNumber == null ? null : phoneNumber.replace("-", "").trim();
    }
    
    @Transactional(readOnly = true)
    public SubscriptionMeResponse getMySubscription(Long userId) {

        Subscriptions subscription = subscriptionsRepository.findByUser_Id(userId)
                .orElseThrow(() -> new IllegalArgumentException("구독 정보가 없습니다."));

        LocalDateTime now = LocalDateTime.now();

        SubscriptionStatus status = subscription.getSubscriptionStatus();
        LocalDateTime expiresAt = subscription.getExpiresAt();

        boolean notExpired = expiresAt != null && expiresAt.isAfter(now);

        
        boolean paid = notExpired && (status == SubscriptionStatus.ACTIVE || status == SubscriptionStatus.CANCELED);

        
        String displayStatus = notExpired ? status.name() : SubscriptionStatus.EXPIRED.name();

        return SubscriptionMeResponse.builder()
                .subscriptionId(subscription.getId())
                .grade(subscription.getPlanType())
                .subscriptionStatus(status)
                .displayStatus(displayStatus)
                .startedAt(subscription.getStartedAt())
                .expiresAt(expiresAt)
                .paid(paid)
                .build();
    }
    
    // 구독 해지 관련
    @Transactional
    public CancelSubscriptionResponse cancelSubscription(Long userId) {

        Subscriptions subscription = subscriptionsRepository.findByUser_Id(userId)
                .orElseThrow(() -> new IllegalArgumentException("구독 정보가 없습니다."));

        LocalDateTime now = LocalDateTime.now();

        // 이미 만료된 경우
        if (!subscription.getExpiresAt().isAfter(now)) {
            subscription.expire();
            throw new IllegalArgumentException("이미 만료된 구독입니다.");
        }

        // 이미 해지 예약된 상태
        if (subscription.getSubscriptionStatus() == SubscriptionStatus.CANCELED) {
            throw new IllegalArgumentException("이미 해지 예약된 구독입니다.");
        }

        subscription.cancel();

        boolean paid = true;

        return CancelSubscriptionResponse.builder()
                .subscriptionId(subscription.getId())
                .grade(subscription.getPlanType())
                .subscriptionStatus(subscription.getSubscriptionStatus()) 
                .expiresAt(subscription.getExpiresAt())
                .paid(paid)
                .build();
    }
}
