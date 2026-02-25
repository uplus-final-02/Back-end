package org.backend.userapi.membership.exception;

public class UplusUserNotFoundException extends RuntimeException {
    public UplusUserNotFoundException(String message) {
        super(message);
    }
}
