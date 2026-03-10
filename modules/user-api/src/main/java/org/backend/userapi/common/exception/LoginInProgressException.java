package org.backend.userapi.common.exception;

/**
 * 동일 이메일로 동시에 로그인 요청이 들어온 경우 (SETNX 락 실패).
 * 클라이언트는 잠시 후 재시도 → GlobalExceptionHandler → HTTP 409 Conflict
 */
public class LoginInProgressException extends RuntimeException {
    public LoginInProgressException(String message) {
        super(message);
    }
}
