package org.backend.userapi.payment.exception;

public class PaymentIdempotencyException extends RuntimeException {
    public PaymentIdempotencyException(String message) {
        super(message);
    }
}
