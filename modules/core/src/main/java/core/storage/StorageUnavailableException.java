package core.storage;

/**
 * MinIO(오브젝트 스토리지)가 Degraded Mode(장애 상태)일 때 발생하는 예외.
 *
 * <p>[발생 시점]
 * <ul>
 *   <li>{@link MinioBucketInitializer}가 @PostConstruct 단계에서 MinIO 연결에 실패해
 *       {@code available = false} 상태로 기동된 경우.
 *   <li>{@link MinioObjectStorageService}의 공개 메서드(presigned URL 발급, stat, 다운로드, 업로드)
 *       진입 시 {@code isAvailable()} 검사에서 false면 즉시 throw.
 * </ul>
 *
 * <p>[응답 전략]
 * <ul>
 *   <li>user-api / admin-api {@code GlobalExceptionHandler}가 이 예외를 HTTP 503으로 변환.
 *   <li>클라이언트는 503 수신 시 재시도(지수 백오프)를 권장.
 * </ul>
 */
public class StorageUnavailableException extends RuntimeException {

    public StorageUnavailableException(String message) {
        super(message);
    }
}
