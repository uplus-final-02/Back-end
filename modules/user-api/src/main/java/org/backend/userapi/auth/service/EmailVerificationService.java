package org.backend.userapi.auth.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.backend.userapi.common.exception.EmailSendFailedException;
import org.backend.userapi.common.exception.InvalidVerificationCodeException;
import org.backend.userapi.common.exception.TooManyEmailRequestsException;
import org.springframework.beans.factory.annotation.Value;
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
     * - 1분 이내 재발송 요청 시 429 예외
     * - 발송 실패 시 Redis 코드 삭제 후 500 예외 → 클라이언트가 즉시 인지 가능
     */
    public void sendCode(String email) {
        // 재발송 쿨다운 체크
        String cooldownKey = COOLDOWN_KEY_PREFIX + email;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(cooldownKey))) {
            long remaining = Optional.ofNullable(redisTemplate.getExpire(cooldownKey)).orElse(COOLDOWN_TTL_SECONDS);
            throw new TooManyEmailRequestsException(
                String.format("인증코드를 이미 발송했습니다. %d초 후 다시 시도해주세요.", remaining)
            );
        }

        String code = generateCode();

        // 인증코드 Redis 저장 (5분)
        redisTemplate.opsForValue()
            .set(VERIFY_KEY_PREFIX + email, code, Duration.ofSeconds(CODE_TTL_SECONDS));

        // 쿨다운 Redis 저장 (1분)
        redisTemplate.opsForValue()
            .set(cooldownKey, "1", Duration.ofSeconds(COOLDOWN_TTL_SECONDS));

        // 동기 이메일 발송 - 실패 시 즉시 예외 전파
        sendEmail(email, code);
    }

    /**
     * 인증코드 검증 후 삭제 (1회용).
     *
     * @throws InvalidVerificationCodeException 코드 불일치 또는 만료
     */
    public void verifyCode(String email, String inputCode) {
        String key = VERIFY_KEY_PREFIX + email;
        String storedCode = redisTemplate.opsForValue().get(key);

        if (storedCode == null) {
            throw new InvalidVerificationCodeException("인증코드가 만료되었습니다. 다시 요청해주세요.");
        }
        if (!storedCode.equals(inputCode)) {
            throw new InvalidVerificationCodeException("인증코드가 올바르지 않습니다.");
        }

        redisTemplate.delete(key); // 검증 성공 시 즉시 삭제 (1회용)
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
            redisTemplate.delete(VERIFY_KEY_PREFIX + to);
            redisTemplate.delete(COOLDOWN_KEY_PREFIX + to);
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
