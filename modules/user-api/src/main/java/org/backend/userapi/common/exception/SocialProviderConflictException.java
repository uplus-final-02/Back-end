package org.backend.userapi.common.exception;

public class SocialProviderConflictException extends RuntimeException {
    public SocialProviderConflictException(String message) {
        super(message);
    }
}
