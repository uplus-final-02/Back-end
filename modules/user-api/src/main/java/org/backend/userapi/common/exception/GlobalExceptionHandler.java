package org.backend.userapi.common.exception;

import core.security.exception.JwtInvalidTokenException;
import core.security.exception.JwtTokenExpiredException;
import lombok.extern.slf4j.Slf4j;
import org.backend.userapi.common.dto.ApiResponse;
import org.backend.userapi.membership.exception.UplusUserNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateEmail(DuplicateEmailException e) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ApiResponse<>(409, e.getMessage(), null));
    }

    @ExceptionHandler(DuplicateNicknameException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateNickname(DuplicateNicknameException e) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ApiResponse<>(409, e.getMessage(), null));
    }

    @ExceptionHandler(SocialProviderConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleSocialProviderConflict(SocialProviderConflictException e) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ApiResponse<>(409, e.getMessage(), null));
    }

    @ExceptionHandler(InvalidVerificationCodeException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidVerificationCode(InvalidVerificationCodeException e) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, e.getMessage(), null));
    }

    @ExceptionHandler(InvalidSetupTokenException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidSetupToken(InvalidSetupTokenException e) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, e.getMessage(), null));
    }

    @ExceptionHandler(JwtTokenExpiredException.class)
    public ResponseEntity<ApiResponse<Void>> handleJwtTokenExpired(JwtTokenExpiredException e) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, e.getMessage(), null));
    }

    @ExceptionHandler(JwtInvalidTokenException.class)
    public ResponseEntity<ApiResponse<Void>> handleJwtInvalidToken(JwtInvalidTokenException e) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, e.getMessage(), null));
    }

    @ExceptionHandler(InvalidTagException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidTag(InvalidTagException e) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, e.getMessage(), null));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidCredentials(InvalidCredentialsException e) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(401, e.getMessage(), null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("입력값이 올바르지 않습니다.");

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, message, null));
    }

    @ExceptionHandler(VideoNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleVideoNotFound(VideoNotFoundException e) {
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND) // 404 상태 코드
            .body(new ApiResponse<>(404, e.getMessage(), null));
    }
    
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(IllegalArgumentException e) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, e.getMessage(), null));
    }
    
    
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleRuntimeException(RuntimeException e) {
        // 보안상 e.getMessage()는 로그로만 남기고 클라이언트엔 숨기는 게 정석이나,
        // 개발 단계에서는 원인 파악을 위해 노출 (운영 시엔 "서버 내부 오류"로 고정 추천)
        log.error("RuntimeException occurred", e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(500, "서버 내부 오류가 발생했습니다: " + e.getMessage(), null));
    }
    
    // (옵션) 최상위 Exception까지 잡고 싶다면 추가
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("Unhandled Exception occurred", e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(500, "알 수 없는 오류가 발생했습니다.", null));
    }

    @ExceptionHandler(ContentNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleContentNotFound(ContentNotFoundException e) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(404, e.getMessage(), null));
    }
    
    //찜 삭제 전용 404 예외 처리
    @ExceptionHandler(BookmarkNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleBookmarkNotFound(BookmarkNotFoundException e) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(404, e.getMessage(), null));
    }
    
    @ExceptionHandler(UplusUserNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleUplusNotFound(UplusUserNotFoundException e) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(404, e.getMessage(), null));
    }
    
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(ConflictException e) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ApiResponse<>(409, e.getMessage(), null));
    }
    
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException e) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ApiResponse<>(409, "이미 다른 계정에서 인증된 전화번호입니다.", null));
    }
}