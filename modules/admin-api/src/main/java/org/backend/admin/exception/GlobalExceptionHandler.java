package org.backend.admin.exception;

import org.springframework.http.ResponseEntity;
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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        ErrorCode ec = ErrorCode.INTERNAL_ERROR;
        return ResponseEntity
                .status(ec.status())
                .body(new ErrorResponse(ec.status().value(), ec.message(), null));
    }
}