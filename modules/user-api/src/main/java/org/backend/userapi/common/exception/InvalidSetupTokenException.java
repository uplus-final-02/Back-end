package org.backend.userapi.common.exception;

public class InvalidSetupTokenException extends RuntimeException {
    public InvalidSetupTokenException(String message) {
        super(message);
    }
}
