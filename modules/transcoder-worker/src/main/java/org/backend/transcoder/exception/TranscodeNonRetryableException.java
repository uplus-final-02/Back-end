package org.backend.transcoder.exception;

public class TranscodeNonRetryableException extends RuntimeException {
    public TranscodeNonRetryableException(String message, Throwable cause) { super(message, cause); }
    public TranscodeNonRetryableException(String message) { super(message); }
}