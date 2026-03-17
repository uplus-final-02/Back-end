package org.backend.transcoder.exception;

public class TranscodeRetryableException extends RuntimeException {
    public TranscodeRetryableException(String message, Throwable cause) { super(message, cause); }
    public TranscodeRetryableException(String message) { super(message); }
}