package org.backend.userapi.auth.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.backend.userapi.common.exception.EmailSendFailedException;
import org.backend.userapi.common.exception.InvalidVerificationCodeException;
import org.backend.userapi.common.exception.RedisServiceUnavailableException;
import org.backend.userapi.common.exception.TooManyEmailRequestsException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private final StringRedisTemplate redisTemplate;
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String mailUsername;

    // Redis key prefix
    private static final String VERIFY_KEY_PREFIX  = "email:verify:";   // 인증코드
    private static final String COOLDOWN_KEY_PREFIX = "email:cooldown:"; // 재발송 제한

    private static final long CODE_TTL_SECONDS     = 300L; // 인증코드 유효시간: 5분
    private static final long COOLDOWN_TTL_SECONDS = 60L;  // 재발송 최소 간격: 1분
    private static final int  CODE_LENGTH          = 6;

    /**
     * 이메일 인증코드 발송 (동기).
     *
     * <ul>
     *   <li>쿨다운 체크와 키 세팅을 {@code setIfAbsent()} (Redis SETNX) 단일 원자 연산으로 처리.
     *       EC2 멀티 인스턴스 환경에서 {@code hasKey()} + {@code set()} 분리 구조의
     *       TOCTOU(Time-Of-Check-Time-Of-Use) 레이스 컨디션으로 인한 중복 발송을 방지한다.</li>
     *   <li>1분 이내 재발송 요청 시 429 예외</li>
     *   <li>Redis 다운 시 쿨다운 체크를 건너뛰고 발송 시도, 코드 저장 실패 시 503 반환</li>
     *   <li>발송 실패 시 Redis 코드·쿨다운 삭제 후 500 예외 → 클라이언트가 즉시 인지 가능</li>
     * </ul>
     */
    public void sendCode(String email) {
        String cooldownKey = COOLDOWN_KEY_PREFIX + email;
        boolean cooldownSet = false;

        // ── 쿨다운 체크 + 세팅: 단일 원자 연산 (SETNX) ──────────────────
        // setIfAbsent(): 키가 없으면 SET+EXPIRE, 있으면 아무것도 안 함 → true/false 반환
        // hasKey() + set() 분리 시 두 명령 사이에 다른 인스턴스가 끼어들어 중복 발송 가능
        try {
            Boolean isNew = redisTemplate.opsForValue()
                    .setIfAbsent(cooldownKey, "1", Duration.ofSeconds(COOLDOWN_TTL_SECONDS));
            if (!Boolean.TRUE.equals(isNew)) {
                // 이미 쿨다운 키 존재 → 재발송 제한 중
                long remaining = Optional.ofNullable(redisTemplate.getExpire(cooldownKey))
                                         .orElse(COOLDOWN_TTL_SECONDS);
                throw new TooManyEmailRequestsException(
                    String.format("인증코드를 이미 발송했습니다. %d초 후 다시 시도해주세요.", remaining)
                );
            }
            cooldownSet = true;
        } catch (TooManyEmailRequestsException e) {
            throw e; // 비즈니스 예외는 그대로 전파
        } catch (RedisConnectionFailureException e) {
            // Redis 다운 시 쿨다운 체크 생략하고 발송 진행
            // SMTP 자체가 rate limit 역할을 하므로 보안상 허용 가능
            log.warn("[Redis DOWN] 이메일 쿨다운 확인 실패 - 쿨다운 체크 건너뜀: email={}", email);
        }

        String code = generateCode();

        // ── 인증코드 저장 (Redis 다운 시 cooldown 롤백 후 503) ────────────
        try {
            redisTemplate.opsForValue()
                .set(VERIFY_KEY_PREFIX + email, code, Duration.ofSeconds(CODE_TTL_SECONDS));
        } catch (RedisConnectionFailureException e) {
            if (cooldownSet) {
                // setIfAbsent로 세팅된 쿨다운 롤백: 코드 저장 실패로 사용자가 재시도 가능해야 함
                try { redisTemplate.delete(cooldownKey); } catch (Exception ignored) {}
            }
            log.error("[Redis DOWN] 이메일 인증코드 저장 실패 - 인증 서비스 불가: email={}", email);
            throw new RedisServiceUnavailableException(
                "인증 서비스가 일시적으로 이용 불가합니다. 잠시 후 다시 시도해주세요."
            );
        }

        // 동기 이메일 발송 - 실패 시 즉시 예외 전파
        sendEmail(email, code);
    }

    /**
     * 인증코드 검증 후 삭제 (1회용).
     * - Redis 다운 시 503 반환
     *
     * @throws InvalidVerificationCodeException 코드 불일치 또는 만료
     * @throws RedisServiceUnavailableException Redis 연결 실패
     */
    public void verifyCode(String email, String inputCode) {
        String key = VERIFY_KEY_PREFIX + email;
        String storedCode;

        // Redis 연결 실패 방어
        try {
            storedCode = redisTemplate.opsForValue().get(key);
        } catch (RedisConnectionFailureException e) {
            log.error("[Redis DOWN] 이메일 인증코드 조회 실패 - 인증 서비스 불가: email={}", email);
            throw new RedisServiceUnavailableException(
                "인증 서비스가 일시적으로 이용 불가합니다. 잠시 후 다시 시도해주세요."
            );
        }

        if (storedCode == null) {
            throw new InvalidVerificationCodeException("인증코드가 만료되었습니다. 다시 요청해주세요.");
        }
        if (!storedCode.equals(inputCode)) {
            throw new InvalidVerificationCodeException("인증코드가 올바르지 않습니다.");
        }

        // 검증 성공 시 즉시 삭제 (1회용) — 삭제 실패는 TTL로 자동 만료되므로 치명적이지 않음
        try {
            redisTemplate.delete(key);
        } catch (RedisConnectionFailureException e) {
            log.warn("[Redis DOWN] 인증코드 삭제 실패 (TTL 만료로 자동 삭제 예정): email={}", email);
            // 코드 일치 확인은 완료됐으므로 계속 진행
        }
    }

    // =========================================================
    // Private
    // =========================================================

    private void sendEmail(String to, String code) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");

            helper.setFrom(mailUsername);
            helper.setTo(to);
            helper.setSubject("[Monghyang] 이메일 인증코드");
            helper.setText(buildEmailHtml(code), true); // true = HTML

            mailSender.send(message);
            log.info("[이메일 발송 성공] to={}", to);

        } catch (MessagingException | MailException e) {
            log.error("[이메일 발송 실패] to={}, error={}", to, e.getMessage());
            // 발송 실패 시 Redis 코드·쿨다운 삭제 → 사용자가 바로 재요청 가능
            try {
                redisTemplate.delete(VERIFY_KEY_PREFIX + to);
                redisTemplate.delete(COOLDOWN_KEY_PREFIX + to);
            } catch (RedisConnectionFailureException redisEx) {
                log.warn("[Redis DOWN] 이메일 발송 실패 후 코드 삭제도 실패: email={}", to);
            }
            throw new EmailSendFailedException("이메일 발송에 실패했습니다. 잠시 후 다시 시도해주세요.");
        }
    }

    private String buildEmailHtml(String code) {
        return """
                <div style="font-family: Arial, sans-serif; max-width: 480px; margin: 0 auto; padding: 32px; border: 1px solid #e5e7eb; border-radius: 8px;">
                    <h2 style="color: #1f2937; margin-bottom: 8px;">이메일 인증</h2>
                    <p style="color: #6b7280; margin-bottom: 24px;">아래 인증코드를 입력해 주세요.</p>
                    <div style="background: #f3f4f6; border-radius: 6px; padding: 20px; text-align: center;">
                        <span style="font-size: 32px; font-weight: bold; letter-spacing: 8px; color: #111827;">%s</span>
                    </div>
                    <p style="color: #9ca3af; font-size: 13px; margin-top: 20px;">
                        인증코드는 <strong>5분</strong> 후 만료됩니다.<br>
                        본인이 요청하지 않은 경우 이 이메일을 무시해 주세요.
                    </p>
                </div>
                """.formatted(code);
    }

    private String generateCode() {
        SecureRandom random = new SecureRandom();
        int code = random.nextInt((int) Math.pow(10, CODE_LENGTH));
        return String.format("%0" + CODE_LENGTH + "d", code);
    }
}
