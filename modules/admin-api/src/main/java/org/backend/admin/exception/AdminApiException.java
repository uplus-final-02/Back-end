package org.backend.admin.exception;

import lombok.Getter;

@Getter
public class AdminApiException extends RuntimeException {

    private final ErrorCode errorCode;

    public AdminApiException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    public AdminApiException(ErrorCode errorCode, String customMessage) {
        super(customMessage);
        this.errorCode = errorCode;
    }

}