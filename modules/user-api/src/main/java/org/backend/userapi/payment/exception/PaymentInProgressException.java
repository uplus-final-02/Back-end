package org.backend.userapi.payment.exception;

/**
 * 동일한 Idempotency-Key로 결제가 이미 처리 중일 때 던지는 예외.
 * → 409 Conflict: 클라이언트는 잠시 후 재시도해야 함 (400과 구분)
 */
public class PaymentInProgressException extends RuntimeException {
    public PaymentInProgressException(String message) {
        super(message);
    }
}
