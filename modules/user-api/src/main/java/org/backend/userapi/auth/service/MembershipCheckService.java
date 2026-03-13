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

    /**
     * 유료 구독 여부 확인.
     *
     * <p>{@link Propagation#REQUIRES_NEW}로 독립 트랜잭션을 시작한다.
     * DB 장애 시 예외가 호출부까지 전파되므로, <b>JWT claim 계산(fail-safe)</b>이 필요한
     * 호출부({@link AuthService})에서 {@code DataAccessException}을 catch해 false로 폴백하고,
     * <b>결제/정책 판단(fail-closed)</b>이 필요한 호출부({@link org.backend.userapi.payment.service.PaymentService})는
     * 예외를 그대로 전파해 503으로 응답한다.
     */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public boolean isPaid(Long userId) {
        Subscriptions sub = subscriptionsRepository.findByUser_Id(userId).orElse(null);
        if (sub == null) return false;

        LocalDateTime now = LocalDateTime.now();
        boolean notExpired = sub.getExpiresAt() != null && sub.getExpiresAt().isAfter(now);

        SubscriptionStatus status = sub.getSubscriptionStatus();
        return notExpired && (status == SubscriptionStatus.ACTIVE || status == SubscriptionStatus.CANCELED);
    }

    /**
     * U+ 인증 여부 확인. 통신사 탈퇴 시 {@link UserUplusVerified#revoke}로 인증 상태를 즉시 철회한다.
     *
     * <p>{@link Propagation#REQUIRES_NEW}로 독립 트랜잭션을 시작한다.
     * DB 장애 시 예외가 호출부까지 전파되므로, JWT claim 계산(fail-safe)과
     * 결제/정책 판단(fail-closed) 각각의 호출부가 의도에 맞게 처리한다.
     * ({@link MembershipCheckService#isPaid} Javadoc 참고)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)  // verified.revoke() 쓰기 발생 → readOnly 제거
    public boolean isUplus(Long userId) {
        UserUplusVerified verified = userUplusVerifiedRepository.findByUser_Id(userId).orElse(null);

        if (verified == null || !verified.isVerified()) {
            return false;
        }

        boolean activeTelecomMember = telecomMemberRepository
                .existsByPhoneNumberAndStatus(verified.getPhoneNumber(), "ACTIVE");

        // 통신사 회원 탈퇴 시 인증 상태 즉시 철회
        if (!activeTelecomMember) {
            verified.revoke(LocalDateTime.now());
            return false;
        }

        return true;
    }
}
