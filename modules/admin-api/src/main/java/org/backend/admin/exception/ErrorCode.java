package org.backend.admin.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    // 404
    CONTENT_NOT_FOUND(HttpStatus.NOT_FOUND, "CONTENT_NOT_FOUND", "콘텐츠를 찾을 수 없습니다."),

    // 409
    UPLOAD_NOT_COMPLETED(HttpStatus.CONFLICT, "UPLOAD_NOT_COMPLETED", "MinIO에 업로드된 파일이 없습니다. PUT 업로드부터 완료하세요."),

    // 400
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "요청 값이 올바르지 않습니다."),

    // 500
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "서버 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }
    public String message() { return message; }
}