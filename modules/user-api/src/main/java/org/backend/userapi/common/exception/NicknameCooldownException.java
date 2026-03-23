package org.backend.userapi.common.exception;

public class NicknameCooldownException extends RuntimeException {

    public NicknameCooldownException(String message) {
        super(message);
    }
}
