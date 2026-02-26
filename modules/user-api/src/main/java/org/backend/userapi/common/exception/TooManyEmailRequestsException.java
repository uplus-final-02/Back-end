package org.backend.userapi.common.exception;

public class TooManyEmailRequestsException extends RuntimeException {
    public TooManyEmailRequestsException(String message) {
        super(message);
    }
}
