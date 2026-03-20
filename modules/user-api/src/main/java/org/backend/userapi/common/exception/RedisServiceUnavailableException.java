package org.backend.userapi.common.exception;

public class RedisServiceUnavailableException extends RuntimeException {
    public RedisServiceUnavailableException(String message) {
        super(message);
    }
}
