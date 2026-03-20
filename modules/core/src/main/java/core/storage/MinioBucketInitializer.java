package core.storage;

import core.storage.config.StorageProperties;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 애플리케이션 기동 시 MinIO 버킷 존재 여부를 확인하고, 없으면 생성하는 초기화 컴포넌트.
 *
 * <p>[Degraded Mode 전략]
 * <ul>
 *   <li>MinIO 연결 실패 시 예외를 throw하지 않고 {@code available = false}로 전환.
 *   <li>앱은 정상 기동되며, 스토리지 의존 기능만 503으로 응답.

 * </ul>
 *
 * <p>[복구 감지]
 * <ul>
 *   <li>{@link #healthCheck()}가 30초마다 MinIO 연결을 ping해 {@code available} 플래그를 최신 상태로 유지.
 *   <li>MinIO 복구 감지 → {@code available=true} 자동 전환 → 재기동 없이 서비스 복구.
 *   <li>런타임 장애 감지 → {@code available=false} 자동 전환 → 이후 요청은 즉시 503 반환.
 *   <li>동작 조건: 애플리케이션에 {@code @EnableScheduling} 필요.
 * </ul>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.storage.s3.provider", havingValue = "minio", matchIfMissing = true)
public class MinioBucketInitializer {

    private final MinioClient internalMinioClient;
    private final StorageProperties props;

    /**
     * MinIO 가용 여부 플래그.
     * @PostConstruct 성공 → true / 실패 또는 런타임 장애 감지 → false (Degraded Mode).
     */
    private final AtomicBoolean available = new AtomicBoolean(false);

    public MinioBucketInitializer(
            @Qualifier("internalMinioClient") MinioClient internalMinioClient,
            StorageProperties props
    ) {
        this.internalMinioClient = internalMinioClient;
        this.props = props;
    }

    @PostConstruct
    public void init() {
        try {
            boolean exists = internalMinioClient.bucketExists(
                    BucketExistsArgs.builder()
                            .bucket(props.bucket())
                            .build()
            );

            if (!exists) {
                internalMinioClient.makeBucket(
                        MakeBucketArgs.builder()
                                .bucket(props.bucket())
                                .build()
                );
                log.info("[MinIO] bucket created: {}", props.bucket());
            } else {
                log.info("[MinIO] bucket exists: {}", props.bucket());
            }

            available.set(true);
            log.info("[MinIO] 스토리지 정상 연결 — available=true");

        } catch (Exception e) {
            // 예외를 throw하지 않아 앱 기동은 계속됨 (Degraded Mode)
            log.warn("[MinIO] bucket init 실패 — Degraded Mode 전환 (스토리지 기능 비활성): " +
                     "bucket={}, error={}", props.bucket(), e.getMessage());
            // available 은 false 유지 → healthCheck()로 복구 감지
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Public API
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * MinIO 버킷 초기화(및 최신 health check) 성공 여부 반환.
     */
    public boolean isAvailable() {
        return available.get();
    }

    /**
     * MinIO가 Degraded Mode이면 {@link StorageUnavailableException} throw.
     **
     * 각 메서드 진입 시 공통으로 호출해 중복 로직 없이 Degraded Mode를 감지.
     *
     * @throws StorageUnavailableException MinIO가 이용 불가 상태일 때
     */
    public void assertAvailable() {
        if (!available.get()) {
            throw new StorageUnavailableException(
                    "[MinIO] 스토리지 서비스가 현재 이용 불가 상태입니다. 잠시 후 다시 시도해주세요.");
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Health Check (복구/장애 자동 감지)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * 30초마다 MinIO 연결 상태를 ping하고 {@code available} 플래그를 갱신.
     *
     * <ul>
     *   <li>ping 성공 + 이전 false → {@code available=true} 전환 (복구 감지).
     *   <li>ping 실패 + 이전 true  → {@code available=false} 전환 (장애 감지).
     *   <li>상태 변화 없으면 로그 미출력 (노이즈 방지).
     * </ul>
     *
     * <p>동작 조건: 애플리케이션 컨텍스트에 {@code @EnableScheduling} 선언 필요.
     */
    @Scheduled(fixedDelay = 30_000)
    public void healthCheck() {
        boolean prev = available.get();
        try {
            internalMinioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(props.bucket()).build());

            if (!prev) {
                available.set(true);
                log.info("[MinIO] 연결 복구 감지 — available=true: bucket={}", props.bucket());
            }
        } catch (Exception e) {
            if (prev) {
                available.set(false);
                log.warn("[MinIO] 연결 끊김 감지 — available=false: bucket={}, error={}",
                        props.bucket(), e.getMessage());
            }
        }
    }
}
