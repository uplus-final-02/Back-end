package org.backend.userapi.common.exception;

public class OAuthLoginException extends RuntimeException {
    public OAuthLoginException(String message) {
        super(message);
    }
    public OAuthLoginException(String message, Throwable cause) {
        super(message, cause);
    }
}
