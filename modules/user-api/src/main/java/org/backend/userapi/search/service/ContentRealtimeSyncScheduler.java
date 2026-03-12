package org.backend.userapi.search.service;

import content.entity.Content;
import content.repository.ContentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.backend.userapi.common.scheduler.SchedulerWatermarkJdbcRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * DB에서 변경된 콘텐츠를 주기적으로 Elasticsearch에 인덱싱하는 실시간 동기화 스케줄러.
 *
 * <p>[워터마크 공유 전략 — 3계층 폴백]
 * <pre>
 *   1. Redis  (빠른 읽기/쓰기, 정상 경로)
 *      ↓ Redis 장애 or 파싱 오류
 *   2. MySQL  (scheduler_watermark 테이블, REQUIRES_NEW 트랜잭션)
 *      Redis down + 인스턴스 교대 시에도 워터마크 일관성 보장
 *      ↓ MySQL도 장애
 *   3. 인메모리 lastSyncedAt  (중복 인덱싱 가능하지만 데이터 손상 없음)
 * </pre>
 *
 * <p>[예외 처리 원칙]
 * <ul>
 *   <li>Redis {@link DataAccessException}: 연결 장애 — warn 로그, 폴백 진행
 *   <li>{@link DateTimeParseException}: 저장값 형식 오류(코드 버그) — error 로그, 폴백 진행
 *   <li>두 예외를 분리해 코드 버그를 조용히 삼키지 않도록 함
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContentRealtimeSyncScheduler {

    private static final String WATERMARK_KEY  = "scheduler:es-sync:watermark";
    private static final String SCHEDULER_NAME = "esSyncTask";

    private final ContentRepository contentRepository;
    private final ContentIndexingService contentIndexingService;
    private final StringRedisTemplate redisTemplate;
    private final SchedulerWatermarkJdbcRepository watermarkRepository;

    @Value("${app.search.realtime-sync.enabled:true}")
    private boolean realtimeSyncEnabled;

    /**
     * 인메모리 폴백 워터마크 (3계층 중 최후 수단).
     * Redis/MySQL 정상 시에는 항상 두 저장소를 사용하고, 이 값은 최신으로 동기화됨.
     */
    private LocalDateTime lastSyncedAt = LocalDateTime.now().minusMinutes(5);

    @Scheduled(fixedDelayString = "${app.search.realtime-sync.interval-ms:30000}")
    @SchedulerLock(
            name           = "esSyncTask",
            lockAtMostFor  = "PT10M",  // 크래시 안전망 — 실행 중 앱이 죽어도 10분 후 자동 해제
            lockAtLeastFor = "PT5S"    // 최소 5초 유지 — 정상 완료 직후 다른 인스턴스 즉시 실행 방지
    )
    @Transactional(readOnly = true)
    public void syncUpdatedContents() {
        if (!realtimeSyncEnabled) {
            return;
        }

        // ── 1. 워터마크 로드 (Redis → MySQL → 인메모리) ──────────────────
        LocalDateTime watermark = loadWatermark();

        List<Content> updatedContents = contentRepository.findByUpdatedAtAfter(watermark);
        if (updatedContents.isEmpty()) {
            saveWatermark(LocalDateTime.now());
            return;
        }

        int successCount = 0;
        int failCount = 0;

        for (Content content : updatedContents) {
            try {
                contentIndexingService.indexContent(content.getId());
                successCount++;
            } catch (DataAccessException e) {
                // ES 다운 시: 해당 콘텐츠만 건너뛰고 다음 항목 계속 처리
                // 예외를 전파하지 않아 매 실행마다 에러 로그 폭발 방지
                failCount++;
                log.warn("[ES Sync] 콘텐츠 인덱싱 실패 - ES 연결 문제로 건너뜀 (contentId={}): {}",
                        content.getId(), e.getMessage());
            } catch (Exception e) {
                // 그 외 예상치 못한 예외도 catch하여 스케줄러 지속 실행 보장
                failCount++;
                log.warn("[ES Sync] 콘텐츠 인덱싱 실패 - 알 수 없는 오류 (contentId={}): {}",
                        content.getId(), e.getMessage());
            }
        }

        if (failCount > 0) {
            // 보수 전략:
            // 실패가 1건이라도 있으면 워터마크(lastSyncedAt)를 전진시키지 않는다.
            // → 다음 주기에 같은 구간을 다시 읽어 실패 건 누락을 방지
            log.warn("[ES Sync] 실시간 동기화 부분 실패. success={}, fail={}, watermark 유지={}",
                    successCount, failCount, watermark);
        } else {
            LocalDateTime newWatermark = updatedContents.stream()
                    .map(Content::getUpdatedAt)
                    .filter(updatedAt -> updatedAt != null)
                    .max(LocalDateTime::compareTo)
                    .orElse(LocalDateTime.now());

            // ── 2. 워터마크 저장 (MySQL → Redis → 인메모리) ──────────────
            saveWatermark(newWatermark);
            log.debug("[ES Sync] 실시간 동기화 완료. syncedCount={}, watermark={}",
                    successCount, newWatermark);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  워터마크 3계층 폴백
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * 워터마크 로드 (Redis → MySQL → 인메모리).
     *
     * <p>[예외 분리 — catch (Exception e) 지양]
     * <ul>
     *   <li>{@link DataAccessException}: Redis 연결 장애 → warn, MySQL 폴백
     *   <li>{@link DateTimeParseException}: 저장값 형식 오류(코드 버그) → error, MySQL 폴백
     *   <li>두 예외를 분리해 "Redis 장애"와 "코드 버그"를 다른 심각도로 처리
     * </ul>
     */
    private LocalDateTime loadWatermark() {

        // 1단계: Redis (fast path)
        String stored = null;
        try {
            stored = redisTemplate.opsForValue().get(WATERMARK_KEY);
        } catch (DataAccessException e) {
            log.warn("[ES Sync] Redis 워터마크 조회 실패 — MySQL 폴백 시도: {}", e.getMessage());
        }

        if (stored != null) {
            try {
                return LocalDateTime.parse(stored);
            } catch (DateTimeParseException e) {
                // 코드/데이터 버그 — warn이 아닌 error로 명시 (조용히 삼키지 않음)
                log.error("[ES Sync] Redis 워터마크 파싱 실패 (형식 오류, 코드 확인 필요) " +
                          "— MySQL 폴백 시도: stored='{}', error={}", stored, e.getMessage());
            }
        }

        // 2단계: MySQL fallback (Redis down or parse error)
        try {
            return watermarkRepository.load(SCHEDULER_NAME).orElse(lastSyncedAt);
        } catch (DataAccessException e) {
            log.warn("[ES Sync] MySQL 워터마크 조회도 실패 — 인메모리 폴백: {}", e.getMessage());
        }

        // 3단계: 인메모리 (최후 수단)
        return lastSyncedAt;
    }

    /**
     * 워터마크 저장 (MySQL 우선 → Redis 추가 → 인메모리).
     *
     * <p>[저장 우선순위]
     * <ol>
     *   <li>인메모리 즉시 업데이트 (어떤 경우에도 최신 유지)
     *   <li>MySQL UPSERT ({@code REQUIRES_NEW} — 외부 readOnly 트랜잭션과 독립, 항상 시도)
     *   <li>Redis 저장 (best-effort, 다음 주기 빠른 읽기용)
     * </ol>
     *
     * <p>[예외 처리]
     * {@link DataAccessException}만 캐치. NPE 등 코드 버그는 삼키지 않고 전파.
     */
    private void saveWatermark(LocalDateTime newWatermark) {
        lastSyncedAt = newWatermark;   // 인메모리 항상 최신 유지 (3단계 폴백용)

        // MySQL UPSERT: REQUIRES_NEW → readOnly TX와 독립적으로 커밋
        try {
            watermarkRepository.save(SCHEDULER_NAME, newWatermark);
        } catch (DataAccessException e) {
            log.warn("[ES Sync] MySQL 워터마크 저장 실패 (인메모리 유지): {}", e.getMessage());
        }

        // Redis write: best-effort (다음 주기 빠른 읽기용)
        try {
            redisTemplate.opsForValue().set(WATERMARK_KEY, newWatermark.toString());
        } catch (DataAccessException e) {
            log.warn("[ES Sync] Redis 워터마크 저장 실패 — MySQL이 소스 오브 트루스로 동작 중: {}", e.getMessage());
        }
    }
}
