package org.backend.userapi.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import user.entity.Subscriptions;
import user.entity.UserUplusVerified;
import user.repository.SubscriptionsRepository;
import user.repository.UserUplusVerifiedRepository;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import common.enums.SubscriptionStatus;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class MembershipCheckService {

	private final SubscriptionsRepository subscriptionsRepository;
    private final UserUplusVerifiedRepository userUplusVerifiedRepository;

    /**
     * 유료 구독 여부 확인.
     * <p>MySQL 순간 장애 시 {@link DataAccessException}을 catch하여 false 반환.
     * isPaid는 JWT 클레임용(nice-to-have)이므로 DB 오류가 로그인 전체 실패로 이어지지 않도록 방어한다.
     */
    @Transactional(readOnly = true)
    public boolean isPaid(Long userId) {
        try {
            Subscriptions sub = subscriptionsRepository.findByUser_Id(userId).orElse(null);
            if (sub == null) return false;

            LocalDateTime now = LocalDateTime.now();
            boolean notExpired = sub.getExpiresAt() != null && sub.getExpiresAt().isAfter(now);

            SubscriptionStatus status = sub.getSubscriptionStatus();
            return notExpired && (status == SubscriptionStatus.ACTIVE || status == SubscriptionStatus.CANCELED);
        } catch (DataAccessException e) {
            log.warn("[MembershipCheck] DB 조회 실패 — paid=false 폴백 (userId={}): {}", userId, e.getMessage());
            return false;
        }
    }

    /**
     * U+ 인증 여부 확인.
     * <p>MySQL 순간 장애 시 {@link DataAccessException}을 catch하여 false 반환.
     * isUplus는 JWT 클레임용(nice-to-have)이므로 DB 오류가 로그인 전체 실패로 이어지지 않도록 방어한다.
     */
    @Transactional(readOnly = true)
    public boolean isUplus(Long userId) {
        try {
            UserUplusVerified verified = userUplusVerifiedRepository.findByUser_Id(userId).orElse(null);
            return verified != null && verified.isVerified();
        } catch (DataAccessException e) {
            log.warn("[MembershipCheck] DB 조회 실패 — uplus=false 폴백 (userId={}): {}", userId, e.getMessage());
            return false;
        }
    }
}
