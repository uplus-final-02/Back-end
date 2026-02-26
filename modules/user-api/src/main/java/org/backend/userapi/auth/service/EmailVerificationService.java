package org.backend.userapi.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.backend.userapi.common.exception.InvalidVerificationCodeException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private final StringRedisTemplate redisTemplate;

    private static final String KEY_PREFIX = "email:verify:";
    private static final long CODE_TTL_SECONDS = 300L; // 5분
    private static final int CODE_LENGTH = 6;

    /**
     * 이메일 인증코드 발송.
     * 6자리 코드를 생성해 Redis에 저장하고, 콘솔에 출력합니다.
     * (실제 서비스에서는 MailService 연동 필요)
     */
    public void sendCode(String email) {
        String code = generateCode();
        redisTemplate.opsForValue().set(KEY_PREFIX + email, code, Duration.ofSeconds(CODE_TTL_SECONDS));
        // TODO: 실제 서비스에서는 아래를 메일 발송으로 교체
        log.info("[이메일 인증코드] {} → {}", email, code);
    }

    /**
     * 인증코드 검증 후 삭제 (1회용).
     *
     * @throws InvalidVerificationCodeException 코드 불일치 또는 만료
     */
    public void verifyCode(String email, String inputCode) {
        String key = KEY_PREFIX + email;
        String storedCode = redisTemplate.opsForValue().get(key);

        if (storedCode == null) {
            throw new InvalidVerificationCodeException("인증코드가 만료되었습니다. 다시 요청해주세요.");
        }
        if (!storedCode.equals(inputCode)) {
            throw new InvalidVerificationCodeException("인증코드가 올바르지 않습니다.");
        }

        redisTemplate.delete(key); // 사용 후 즉시 삭제
    }

    private String generateCode() {
        SecureRandom random = new SecureRandom();
        int code = random.nextInt((int) Math.pow(10, CODE_LENGTH));
        return String.format("%0" + CODE_LENGTH + "d", code);
    }
}
