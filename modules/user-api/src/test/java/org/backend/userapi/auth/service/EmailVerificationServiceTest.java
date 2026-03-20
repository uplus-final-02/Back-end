package org.backend.userapi.auth.service;

import org.backend.userapi.common.exception.RedisServiceUnavailableException;
import org.backend.userapi.common.exception.TooManyEmailRequestsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.mail.internet.MimeMessage;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmailVerificationService.sendCode()")
class EmailVerificationServiceTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private JavaMailSender mailSender;
    @Mock private MimeMessage mimeMessage;

    private EmailVerificationService emailVerificationService;

    private static final String EMAIL       = "test@example.com";
    private static final String COOLDOWN_KEY = "email:cooldown:" + EMAIL;
    private static final String VERIFY_KEY   = "email:verify:"   + EMAIL;

    @BeforeEach
    void setUp() {
        emailVerificationService = new EmailVerificationService(redisTemplate, mailSender);
        ReflectionTestUtils.setField(emailVerificationService, "mailUsername", "noreply@example.com");

        // ValueOperations 공통 스텁
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    // ─────────────────────────────────────────────────────────────
    //  정상 케이스
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("최초 요청 — setIfAbsent() true 반환 시 인증코드 저장 후 이메일 발송")
    void sendCode_firstRequest_storeCodeAndSendEmail() {
        // given: 쿨다운 키 없음 → setIfAbsent true (신규 세팅 성공)
        when(valueOps.setIfAbsent(eq(COOLDOWN_KEY), eq("1"), any(Duration.class)))
                .thenReturn(true);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        // when & then: 예외 없이 완료
        assertThatCode(() -> emailVerificationService.sendCode(EMAIL))
                .doesNotThrowAnyException();

        // 인증코드 저장 1회
        verify(valueOps, times(1)).set(eq(VERIFY_KEY), anyString(), any(Duration.class));
        // 이메일 발송 1회
        verify(mailSender, times(1)).send(mimeMessage);
    }

    // ─────────────────────────────────────────────────────────────
    //  쿨다운 중복 발송 방지
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("쿨다운 중 재요청 — setIfAbsent() false 반환 시 429 예외")
    void sendCode_withinCooldown_throws429() {
        // given: 이미 쿨다운 키 존재 → setIfAbsent false
        when(valueOps.setIfAbsent(eq(COOLDOWN_KEY), eq("1"), any(Duration.class)))
                .thenReturn(false);
        when(redisTemplate.getExpire(COOLDOWN_KEY)).thenReturn(45L);

        // when & then
        assertThatThrownBy(() -> emailVerificationService.sendCode(EMAIL))
                .isInstanceOf(TooManyEmailRequestsException.class)
                .hasMessageContaining("45초 후 다시 시도해주세요.");

        // 이메일 발송 없음
        verify(mailSender, never()).send(any(MimeMessage.class));
        // 인증코드 저장 없음
        verify(valueOps, never()).set(eq(VERIFY_KEY), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("쿨다운 중 재요청 — getExpire() null 반환 시 기본값 60초로 응답")
    void sendCode_withinCooldown_getExpireNull_usesDefaultTtl() {
        when(valueOps.setIfAbsent(eq(COOLDOWN_KEY), eq("1"), any(Duration.class)))
                .thenReturn(false);
        when(redisTemplate.getExpire(COOLDOWN_KEY)).thenReturn(null);

        assertThatThrownBy(() -> emailVerificationService.sendCode(EMAIL))
                .isInstanceOf(TooManyEmailRequestsException.class)
                .hasMessageContaining("60초 후 다시 시도해주세요.");
    }

    // ─────────────────────────────────────────────────────────────
    //  Redis 다운 시나리오
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("setIfAbsent() 시 Redis 다운 — 쿨다운 체크 건너뛰고 이메일 발송")
    void sendCode_redisDownOnSetIfAbsent_skipsCooldownAndSendsEmail() {
        // given: setIfAbsent → RedisConnectionFailureException
        when(valueOps.setIfAbsent(eq(COOLDOWN_KEY), eq("1"), any(Duration.class)))
                .thenThrow(new RedisConnectionFailureException("Redis down"));
        // 인증코드 저장은 성공
        doNothing().when(valueOps).set(eq(VERIFY_KEY), anyString(), any(Duration.class));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        // when & then: 예외 없이 완료 (쿨다운 체크 skip)
        assertThatCode(() -> emailVerificationService.sendCode(EMAIL))
                .doesNotThrowAnyException();

        verify(mailSender, times(1)).send(mimeMessage);
    }

    @Test
    @DisplayName("인증코드 저장 시 Redis 다운 — setIfAbsent로 세팅된 쿨다운 롤백 후 503")
    void sendCode_redisDownOnCodeStore_rollbacksCooldownAndThrows503() {
        // given: setIfAbsent 성공(true) → 인증코드 저장 실패
        when(valueOps.setIfAbsent(eq(COOLDOWN_KEY), eq("1"), any(Duration.class)))
                .thenReturn(true);
        doThrow(new RedisConnectionFailureException("Redis down"))
                .when(valueOps).set(eq(VERIFY_KEY), anyString(), any(Duration.class));

        // when & then: 503 예외
        assertThatThrownBy(() -> emailVerificationService.sendCode(EMAIL))
                .isInstanceOf(RedisServiceUnavailableException.class);

        // 쿨다운 롤백: delete 호출
        verify(redisTemplate, times(1)).delete(COOLDOWN_KEY);
        // 이메일 발송 없음
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("인증코드 저장 시 Redis 다운 + 롤백 delete 도 실패 — 503 은 정상 반환")
    void sendCode_redisDownOnCodeStore_rollbackAlsoFails_still503() {
        // given: setIfAbsent 성공, 저장 실패, delete 도 실패
        when(valueOps.setIfAbsent(eq(COOLDOWN_KEY), eq("1"), any(Duration.class)))
                .thenReturn(true);
        doThrow(new RedisConnectionFailureException("Redis down"))
                .when(valueOps).set(eq(VERIFY_KEY), anyString(), any(Duration.class));
        when(redisTemplate.delete(anyString()))
                .thenThrow(new RedisConnectionFailureException("Redis down"));

        // when & then: 503 정상 반환 (롤백 실패를 무시하고 예외 전파)
        assertThatThrownBy(() -> emailVerificationService.sendCode(EMAIL))
                .isInstanceOf(RedisServiceUnavailableException.class);

        verify(mailSender, never()).send(any(MimeMessage.class));
    }
}
