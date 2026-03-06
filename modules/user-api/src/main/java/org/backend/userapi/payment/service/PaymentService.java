package org.backend.userapi.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import common.enums.PaymentProvider;
import common.enums.PaymentStatus;
import common.enums.PlanType;
import common.enums.SubscriptionStatus;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.backend.userapi.common.exception.ConflictException;
import org.backend.userapi.common.exception.RedisServiceUnavailableException;
import org.backend.userapi.payment.dto.SubscribeRequest;
import org.backend.userapi.payment.dto.SubscribeResponse;
import org.backend.userapi.payment.exception.PaymentInProgressException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import user.entity.Payment;
import user.entity.Subscriptions;
import user.entity.User;
import user.repository.PaymentRepository;
import user.repository.SubscriptionsRepository;
import user.repository.UserRepository;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final int SUBSCRIPTION_AMOUNT = 4900;
    private static final int SUBSCRIPTION_DAYS = 30;
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    // 동시 요청이 왔을 때 처리 중임을 나타내는 임시 sentinel 값
    private static final String PROCESSING = "PROCESSING";
    // 처리 중 락 유지 시간 (결제 처리가 이 시간 안에 끝나야 함)
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);

    private final UserRepository userRepository;
    private final SubscriptionsRepository subscriptionsRepository;
    private final PaymentRepository paymentRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @Transactional
    public SubscribeResponse subscribe(Long userId, SubscribeRequest request, String idempotencyKey) {
        String redisKey = "payment:idempotency:" + userId + ":" + idempotencyKey;
        // 요청 본문의 식별자: provider 이름으로 해시 (P1 충돌 감지용)
        String requestHash = request.getPaymentProvider().name();

        // ── P0 Fix: SETNX로 원자적 락 획득 ──────────────────────────────
        // GET → 처리 → SET 대신, SET NX(없을 때만) 로 레이스 컨디션 원천 차단
        Boolean acquired;
        try {
            acquired = redisTemplate.opsForValue().setIfAbsent(redisKey, PROCESSING, LOCK_TTL);
        } catch (RedisConnectionFailureException e) {
            // ── P1 Fix: Redis 장애 시 503 → 멱등성 보장 불가 상태에서 결제 진행 안 함 ──
            log.warn("[Idempotency] Redis 연결 실패 - 결제 보류 (key={}): {}", redisKey, e.getMessage());
            throw new RedisServiceUnavailableException("결제 서비스가 일시적으로 이용 불가합니다. 잠시 후 다시 시도해주세요.");
        }

        if (Boolean.FALSE.equals(acquired)) {
            // 키가 이미 존재 → 처리 중이거나 이미 완료된 요청
            String cached;
            try {
                cached = redisTemplate.opsForValue().get(redisKey);
            } catch (RedisConnectionFailureException e) {
                throw new RedisServiceUnavailableException("결제 서비스가 일시적으로 이용 불가합니다. 잠시 후 다시 시도해주세요.");
            }

            if (cached == null || PROCESSING.equals(cached)) {
                // P1 Fix: "처리 중" → 400이 아닌 409 (클라이언트 재시도 대상)
                throw new PaymentInProgressException("동일한 요청이 처리 중입니다. 잠시 후 다시 시도해주세요.");
            }

            // 요청 본문 해시 비교 → 다른 내용이면 409
            try {
                IdempotencyCache cache = objectMapper.readValue(cached, IdempotencyCache.class);
                if (!requestHash.equals(cache.getRequestHash())) {
                    throw new ConflictException(
                            "동일한 Idempotency-Key로 다른 내용의 요청이 이미 처리되었습니다. 새로운 키를 사용해주세요.");
                }
                log.info("[Idempotency] 중복 요청 → 캐시 반환 (userId={}, key={})", userId, idempotencyKey);
                return cache.getResponse();
            } catch (ConflictException e) {
                throw e;
            } catch (Exception e) {
                // P0 Fix: 락 없이 여기까지 왔는데 캐시 파싱까지 실패 → 절대 결제로 진행하지 않음
                // (다른 요청이 PROCESSING 중일 수 있으므로 409로 중단)
                log.warn("[Idempotency] 캐시 파싱 실패 - 락 미보유 상태, 결제 진행 거부 (key={}): {}", redisKey, e.getMessage());
                throw new PaymentInProgressException("결제 요청 상태를 확인할 수 없습니다. 잠시 후 다시 시도해주세요.");
            }
        }

        // ── 결제·구독 처리 (DB 트랜잭션) ─────────────────────────────────
        try {
            SubscribeResponse response = processSubscribe(userId, request);

            // 성공 → 결과를 Redis에 24h 저장 (PROCESSING → 실제 결과로 교체)
            try {
                IdempotencyCache cacheData = new IdempotencyCache(requestHash, response);
                redisTemplate.opsForValue().set(redisKey, objectMapper.writeValueAsString(cacheData), IDEMPOTENCY_TTL);
                log.debug("[Idempotency] 결과 캐싱 완료 (key={})", redisKey);
            } catch (Exception cacheEx) {
                // Redis 저장 실패 → 락 해제 (다음 중복 요청이 재처리 가능하도록)
                safeDelete(redisKey);
                log.warn("[Idempotency] Redis 저장 실패 - 락 해제 (key={}): {}", redisKey, cacheEx.getMessage());
            }

            return response;

        } catch (RuntimeException e) {
            // 결제 실패 → 락 해제 (클라이언트가 수정 후 동일 키로 재시도 가능)
            safeDelete(redisKey);
            throw e;
        }
    }

    private void safeDelete(String redisKey) {
        try {
            redisTemplate.delete(redisKey);
        } catch (Exception ignored) {
            log.warn("[Idempotency] Redis 락 해제 실패 (key={}): TTL 만료 후 자동 해제됨", redisKey);
        }
    }

    /**
     * 실제 결제·구독 처리 로직 (멱등성 체크와 분리)
     */
    private SubscribeResponse processSubscribe(Long userId, SubscribeRequest request) {
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

        // 결제 이력 생성 (Mock)
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
                .planType(subscription.getPlanType())
                .subscriptionId(subscription.getId())
                .expiresAt(subscription.getExpiresAt())
                .build();
    }

    /**
     * Redis에 저장하는 멱등성 캐시 구조.
     * 요청 해시와 응답을 함께 저장해 같은 키로 다른 요청이 오면 409 반환.
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    private static class IdempotencyCache {
        private String requestHash;
        private SubscribeResponse response;
    }
}
