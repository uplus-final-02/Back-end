package org.backend.userapi.common.exception;

import core.security.exception.JwtInvalidTokenException;
import core.security.exception.JwtTokenExpiredException;
import core.storage.StorageException;
import core.storage.StorageUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.backend.userapi.common.dto.ApiResponse;
import org.backend.userapi.common.exception.LoginInProgressException;
import org.backend.userapi.common.exception.LoginLockedException;
import org.backend.userapi.common.exception.OAuthLoginException;
import org.backend.userapi.membership.exception.UplusUserNotFoundException;
import org.backend.userapi.payment.exception.PaymentIdempotencyException;
import org.backend.userapi.payment.exception.PaymentInProgressException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ── 로그인 5회 연속 실패 → 계정 잠금 → 429 ───────────────────────
    @ExceptionHandler(LoginLockedException.class)
    public ResponseEntity<ApiResponse<Void>> handleLoginLocked(LoginLockedException e) {
        log.warn("[Login] 계정 잠금 상태 접근: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new ApiResponse<>(429, e.getMessage(), null));
    }

    // ── 로그인 동시 중복 요청 차단 → 409 ─────────────────────────────
    @ExceptionHandler(LoginInProgressException.class)
    public ResponseEntity<ApiResponse<Void>> handleLoginInProgress(LoginInProgressException e) {
        log.warn("[Login] 동시 로그인 요청 차단: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ApiResponse<>(409, e.getMessage(), null));
    }

    // ── 소셜 로그인 외부 API 실패 → 502 ──────────────────────────────
    @ExceptionHandler(OAuthLoginException.class)
    public ResponseEntity<ApiResponse<Void>> handleOAuthLoginException(OAuthLoginException e) {
        log.warn("[OAuth] 외부 소셜 로그인 API 실패: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_GATEWAY)
                .body(new ApiResponse<>(502, e.getMessage(), null));
    }

    // ── Redis 연결 실패 → 503 ────────────────────────────────────────
    @ExceptionHandler(RedisConnectionFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleRedisConnectionFailure(RedisConnectionFailureException e) {
        log.warn("[Redis] 연결 실패 - 서비스 일시 불가: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiResponse<>(503, "일시적으로 서비스를 이용할 수 없습니다. 잠시 후 다시 시도해주세요.", null));
    }

    // ── Redis 다운 시 서비스 불가 예외 → 503 ──────────────────────────
    @ExceptionHandler(RedisServiceUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleRedisServiceUnavailable(RedisServiceUnavailableException e) {
        log.warn("[Redis Fallback] {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiResponse<>(503, e.getMessage(), null));
    }

    // ── MySQL / ES 등 데이터 저장소 연결 실패 → 503 ──────────────────
    // 서비스 레이어에서 catch하지 못한 DataAccessResourceFailureException이
    // Controller까지 올라왔을 때 RuntimeException(500) 대신 503으로 명확히 응답
    @ExceptionHandler(DataAccessResourceFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataAccessResourceFailure(DataAccessResourceFailureException e) {
        log.warn("[DataAccess] 데이터 저장소 연결 실패 (MySQL/ES): {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiResponse<>(503, "일시적으로 서비스를 이용할 수 없습니다. 잠시 후 다시 시도해주세요.", null));
    }

    // ── MySQL 순간 장애 (락 타임아웃·커넥션 획득 실패 등 일시적 오류) → 503 ──
    // TransientDataAccessException: 재시도하면 성공할 가능성이 있는 일시적 DB 오류
    // (DeadlockLoserDataAccessException, QueryTimeoutException, CannotAcquireLockException 등)
    @ExceptionHandler(TransientDataAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleTransientDataAccess(TransientDataAccessException e) {
        log.warn("[DB] 일시적 DB 오류 — 503 반환: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiResponse<>(503, "일시적으로 서비스를 이용할 수 없습니다. 잠시 후 다시 시도해주세요.", null));
    }

    // ── 존재하지 않는 경로 요청 → 404 ──────────────────────────────────
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(NoResourceFoundException e) {
        log.warn("[404] 존재하지 않는 경로 요청: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(404, "요청한 경로를 찾을 수 없습니다.", null));
    }

    // ── HTTP 메서드 불일치 → 405 ──────────────────────────────────────
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("[405] 지원하지 않는 HTTP 메서드: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(new ApiResponse<>(405, "지원하지 않는 HTTP 메서드입니다.", null));
    }

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

    @ExceptionHandler(TooManyEmailRequestsException.class)
    public ResponseEntity<ApiResponse<Void>> handleTooManyEmailRequests(TooManyEmailRequestsException e) {
        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new ApiResponse<>(429, e.getMessage(), null));
    }

    @ExceptionHandler(EmailSendFailedException.class)
    public ResponseEntity<ApiResponse<Void>> handleEmailSendFailed(EmailSendFailedException e) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(500, e.getMessage(), null));
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
    
    // ── 결제 멱등성 키 누락 (클라이언트 버그) → 400 ──────────────────
    @ExceptionHandler(PaymentIdempotencyException.class)
    public ResponseEntity<ApiResponse<Void>> handlePaymentIdempotency(PaymentIdempotencyException e) {
        log.warn("[Payment] Idempotency-Key 누락: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, e.getMessage(), null));
    }

    // ── 동일 키로 결제 처리 중 (클라이언트 재시도 대상) → 409 ──────────
    // 400과 구분: 클라이언트는 키를 바꾸는 게 아니라 잠시 후 재시도해야 함
    @ExceptionHandler(PaymentInProgressException.class)
    public ResponseEntity<ApiResponse<Void>> handlePaymentInProgress(PaymentInProgressException e) {
        log.warn("[Payment] 중복 요청 처리 중: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ApiResponse<>(409, e.getMessage(), null));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(IllegalArgumentException e) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(400, e.getMessage(), null));
    }
    
    
    // ── MinIO 런타임 오류 (기동 후 장애) → 503 ──────────────────────────
    // checkAvailable()을 통과했더라도 실제 MinIO 호출에서 실패하면 StorageException 발생
    // → 500 대신 503으로 응답해 Degraded Mode 목표를 유지
    @ExceptionHandler(StorageException.class)
    public ResponseEntity<ApiResponse<Void>> handleStorageException(StorageException e) {
        log.warn("[MinIO] 스토리지 런타임 오류 - 503 반환: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiResponse<>(503, "파일 서비스가 일시적으로 이용 불가합니다. 잠시 후 다시 시도해주세요.", null));
    }

    // ── MinIO 장애 (Degraded Mode 시작 후 fast-reject) → 503 ──────────
    @ExceptionHandler(StorageUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleStorageUnavailable(StorageUnavailableException e) {
        log.warn("[MinIO] 스토리지 서비스 장애 - 503 반환: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiResponse<>(503, "파일 서비스가 일시적으로 이용 불가합니다. 잠시 후 다시 시도해주세요.", null));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleRuntimeException(RuntimeException e) {
        log.error("RuntimeException occurred", e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(500, "서버 내부 오류가 발생했습니다.", null));
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