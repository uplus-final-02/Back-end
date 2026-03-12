package org.backend.admin.exception;

import core.storage.StorageException;
import core.storage.StorageUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    public record ErrorResponse(int code, String message, Object data) {}

    @ExceptionHandler(AdminApiException.class)
    public ResponseEntity<ErrorResponse> handleAdminApiException(AdminApiException e) {
        ErrorCode ec = e.getErrorCode();
        return ResponseEntity
                .status(ec.status())
                .body(new ErrorResponse(ec.status().value(), e.getMessage(), null));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        ErrorCode ec = ErrorCode.INVALID_REQUEST;
        return ResponseEntity
                .status(ec.status())
                .body(new ErrorResponse(ec.status().value(), e.getMessage(), null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        ErrorCode ec = ErrorCode.INVALID_REQUEST;
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .orElse(ec.message());

        return ResponseEntity
                .status(ec.status())
                .body(new ErrorResponse(ec.status().value(), msg, null));
    }

    // ── MinIO 런타임 오류 (기동 후 장애) → 503 ──────────────────────────
    @ExceptionHandler(StorageException.class)
    public ResponseEntity<ErrorResponse> handleStorageException(StorageException e) {
        ErrorCode ec = ErrorCode.STORAGE_UNAVAILABLE;
        return ResponseEntity
                .status(ec.status())
                .body(new ErrorResponse(ec.status().value(), ec.message(), null));
    }

    // ── MinIO 장애 (Degraded Mode 시작 후 fast-reject) → 503 ──────────
    @ExceptionHandler(StorageUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleStorageUnavailable(StorageUnavailableException e) {
        ErrorCode ec = ErrorCode.STORAGE_UNAVAILABLE;
        return ResponseEntity
                .status(ec.status())
                .body(new ErrorResponse(ec.status().value(), ec.message(), null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        ErrorCode ec = ErrorCode.INTERNAL_ERROR;
        return ResponseEntity
                .status(ec.status())
                .body(new ErrorResponse(ec.status().value(), ec.message(), null));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(400, "요청 바디가 비어있습니다. (JSON body required)", null));
    }
}