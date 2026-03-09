package org.backend.userapi.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.backend.userapi.common.exception.LoginInProgressException;
import org.backend.userapi.common.exception.LoginLockedException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 이메일 로그인 Rate Limiting & 동시 중복 요청 차단.
 *
 * <p>[Rate Limiting]
 * - 연속 실패 5회 → 15분 잠금 (login:lock:{email})
 * - 실패 카운터: login:fail:{email}, TTL 15분 고정 윈도우 (첫 실패 시 설정)
 * - 잠금 기간 동안 요청 → 429 LoginLockedException
 *
 * <p>[동시 요청 방지]
 * - 동일 이메일로 동시에 로그인 요청 시 SETNX 락 (login:processing:{email}, TTL 5초)
 * - 이미 처리 중이면 → 409 LoginInProgressException
 *
 * <p>[Redis 장애 시]
 * - 모든 Redis 작업은 RedisConnectionFailureException을 catch → 기능 비활성화만 되고 로그인 자체는 허용
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginRateLimitService {

    private final StringRedisTemplate redisTemplate;

    private static final String FAIL_KEY_PREFIX       = "login:fail:";
    private static final String LOCK_KEY_PREFIX       = "login:lock:";
    private static final String PROCESSING_KEY_PREFIX = "login:processing:";
    private static final String REISSUE_KEY_PREFIX    = "login:reissue:";

    private static final int      MAX_FAILURES      = 5;
    private static final Duration LOCK_DURATION     = Duration.ofMinutes(15);
    private static final Duration PROCESSING_TTL    = Duration.ofSeconds(5);
    private static final Duration REISSUE_TTL       = Duration.ofSeconds(5);

    // ──────────────────────────────────────────────────────────────────────────
    //  잠금 확인
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * 계정 잠금 여부 확인.
     * 잠겨 있으면 LoginLockedException(429) 던짐.
     * Redis 장애 시 잠금 체크를 생략하고 로그인 허용 (Rate Limiting 일시 비활성화).
     */
    public void checkLock(String email) {
        try {
            if (Boolean.TRUE.equals(redisTemplate.hasKey(LOCK_KEY_PREFIX + email))) {
                Long remainingSec = redisTemplate.getExpire(LOCK_KEY_PREFIX + email);
                long remainingMin = (remainingSec != null && remainingSec > 0)
                        ? (remainingSec / 60) + 1
                        : LOCK_DURATION.toMinutes();
                throw new LoginLockedException(
                        String.format("로그인 시도 횟수를 초과했습니다. %d분 후 다시 시도해주세요.", remainingMin)
                );
            }
        } catch (LoginLockedException e) {
            throw e; // 비즈니스 예외는 그대로 전파
        } catch (RedisConnectionFailureException e) {
            log.warn("[Redis DOWN] 로그인 잠금 확인 실패 - Rate Limiting 일시 비활성화: email={}", email);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  동시 요청 방지 SETNX 락
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * 동시 로그인 요청 방지 SETNX 락 획득.
     * 이미 처리 중이면 LoginInProgressException(409) 던짐.
     *
     * @return true = 락 획득 성공 (호출자가 finally에서 {@link #releaseProcessingLock} 호출 필요)
     *         false = Redis 장애로 락 없이 통과
     */
    public boolean acquireProcessingLock(String email) {
        try {
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(PROCESSING_KEY_PREFIX + email, "1", PROCESSING_TTL);
            if (Boolean.FALSE.equals(acquired)) {
                throw new LoginInProgressException("이미 로그인 처리 중입니다. 잠시 후 다시 시도해주세요.");
            }
            return true;
        } catch (LoginInProgressException e) {
            throw e;
        } catch (RedisConnectionFailureException e) {
            log.warn("[Redis DOWN] 동시 요청 방지 락 획득 실패 - 중복 요청 방지 비활성화: email={}", email);
            return false; // 락 없이 통과 (기능 비활성화)
        }
    }

    /**
     * 처리 완료 후 동시 요청 방지 락 해제.
     * Redis 장애 시 TTL로 자동 만료 예정 → 무시.
     */
    public void releaseProcessingLock(String email) {
        try {
            redisTemplate.delete(PROCESSING_KEY_PREFIX + email);
        } catch (RedisConnectionFailureException e) {
            log.warn("[Redis DOWN] 동시 요청 방지 락 해제 실패 - TTL(5s) 만료로 자동 해제 예정: email={}", email);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  토큰 재발급 동시 요청 방지 SETNX 락
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * 토큰 재발급 동시 중복 요청 방지 SETNX 락 획득.
     * 동일 userId로 동시에 reissue 호출 시 409 LoginInProgressException 던짐.
     *
     * @return true = 락 획득 성공 (호출자가 finally에서 {@link #releaseReissueLock} 호출 필요)
     *         false = Redis 장애로 락 없이 통과
     */
    public boolean acquireReissueLock(Long userId) {
        try {
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(REISSUE_KEY_PREFIX + userId, "1", REISSUE_TTL);
            if (Boolean.FALSE.equals(acquired)) {
                throw new LoginInProgressException("이미 토큰 재발급이 처리 중입니다. 잠시 후 다시 시도해주세요.");
            }
            return true;
        } catch (LoginInProgressException e) {
            throw e;
        } catch (RedisConnectionFailureException e) {
            log.warn("[Redis DOWN] 토큰 재발급 락 획득 실패 - 중복 요청 방지 비활성화: userId={}", userId);
            return false;
        }
    }

    /**
     * 재발급 처리 완료 후 SETNX 락 해제.
     * Redis 장애 시 TTL로 자동 만료 → 무시.
     */
    public void releaseReissueLock(Long userId) {
        try {
            redisTemplate.delete(REISSUE_KEY_PREFIX + userId);
        } catch (RedisConnectionFailureException e) {
            log.warn("[Redis DOWN] 토큰 재발급 락 해제 실패 - TTL(5s) 만료로 자동 해제 예정: userId={}", userId);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  실패 카운터 관리
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * 로그인 실패 카운트 증가.
     * MAX_FAILURES 도달 시 잠금 키 설정 + 카운터 초기화.
     * Redis 장애 시 무시.
     */
    public void recordFailure(String email) {
        try {
            String failKey = FAIL_KEY_PREFIX + email;

            Long count = redisTemplate.opsForValue().increment(failKey);

            // 첫 실패 시 TTL 설정 (고정 15분 윈도우 — 매 실패마다 갱신하지 않음)
            if (count != null && count == 1) {
                redisTemplate.expire(failKey, LOCK_DURATION);
            }

            if (count != null && count >= MAX_FAILURES) {
                // 5회 도달 → 잠금 + 카운터 리셋
                redisTemplate.opsForValue().set(LOCK_KEY_PREFIX + email, "1", LOCK_DURATION);
                redisTemplate.delete(failKey);
                log.warn("[Login] 로그인 {}회 연속 실패 → 계정 15분 잠금: email={}", count, email);
            } else {
                log.warn("[Login] 로그인 실패 {}/{}회: email={}", count, MAX_FAILURES, email);
            }
        } catch (RedisConnectionFailureException e) {
            log.warn("[Redis DOWN] 로그인 실패 카운트 기록 실패 - Rate Limiting 비활성화: email={}", email);
        }
    }

    /**
     * 로그인 성공 시 실패 카운터 및 잠금 키 초기화.
     * Redis 장애 시 무시.
     */
    public void clearFailure(String email) {
        try {
            redisTemplate.delete(FAIL_KEY_PREFIX + email);
            redisTemplate.delete(LOCK_KEY_PREFIX + email); // 혹시 남은 잠금도 해제
            log.debug("[Login] 로그인 성공 - 실패 카운터 초기화: email={}", email);
        } catch (RedisConnectionFailureException e) {
            log.warn("[Redis DOWN] 로그인 성공 후 실패 카운터 초기화 실패: email={}", email);
        }
    }
}
