package org.backend.userapi.common.exception;

/**
 * 로그인 연속 실패(5회)로 계정이 일시 잠금된 경우.
 * GlobalExceptionHandler → HTTP 429 Too Many Requests
 */
public class LoginLockedException extends RuntimeException {
    public LoginLockedException(String message) {
        super(message);
    }
}
