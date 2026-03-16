package org.backend.userapi.search.service;

import java.time.LocalDateTime;
import java.util.List;

import org.backend.userapi.common.scheduler.SchedulerWatermarkJdbcRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import content.entity.Content;
import content.repository.ContentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * DB에서 변경된 콘텐츠를 주기적으로 Elasticsearch에 인덱싱하는 실시간 동기화 스케줄러.
 *
 * <p>[워터마크 공유 전략 — 3계층 폴백]
 * <pre>
 * 1. Redis  (빠른 읽기/쓰기, 정상 경로)
 *    ↓ Redis 장애 or 파싱 오류
 * 2. MySQL  (scheduler_watermark 테이블, REQUIRES_NEW 트랜잭션)
 *    Redis down + 인스턴스 교대 시에도 워터마크 일관성 보장
 *    ↓ MySQL도 장애
 * 3. 인메모리 lastSyncedAt  (중복 인덱싱 가능하지만 데이터 손상 없음)
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContentRealtimeSyncScheduler {

    private static final String WATERMARK_KEY  = "scheduler:es-sync:watermark";
    private static final String SCHEDULER_NAME = "esSyncTask";
    private static final int CHUNK_SIZE = 500;

    private final ContentRepository contentRepository;
    private final ContentIndexingService contentIndexingService;
    private final StringRedisTemplate redisTemplate;
    private final SchedulerWatermarkJdbcRepository watermarkRepository;
    private final EsSyncFailureService esSyncFailureService;

    @Value("${app.search.realtime-sync.enabled:true}")
    private boolean realtimeSyncEnabled;

    // 인메모리 폴백용
    private LocalDateTime lastSyncedAt = LocalDateTime.now().minusMinutes(5);
    private Long lastSyncedId = 0L;

    @Scheduled(fixedDelayString = "${app.search.realtime-sync.interval-ms:30000}")
    @SchedulerLock(
            name           = "esSyncTask",
            lockAtMostFor  = "PT10M",
            lockAtLeastFor = "PT5S"
    )
    public void syncUpdatedContents() {
        if (!realtimeSyncEnabled) {
            return;
        }

        // ── 1. 워터마크 커서 로드 (Redis → MySQL → 인메모리) ─────────────
        SchedulerWatermarkJdbcRepository.WatermarkCursor cursor = loadWatermarkCursor();
        LocalDateTime watermark = cursor.watermark();
        Long lastId = cursor.lastId();

        int successCount = 0;
        int failCount = 0;

        // ── 2. 커서 기반 배치 처리 ───────────────────────────────────────
        // updatedAt + id 복합 커서로 동일 updatedAt 중복 읽기 방지
        List<Content> chunk;
        do {
            chunk = contentRepository.findUpdatedAfterCursor(
                    watermark, lastId, PageRequest.of(0, CHUNK_SIZE));

            for (Content content : chunk) {
                try {
                    contentIndexingService.indexContent(content.getId());
                    successCount++;
                } catch (DataAccessException e) {
                    failCount++;
                    esSyncFailureService.record(content.getId(), e.getMessage());
                    log.warn("[ES Sync] ES 연결 문제로 인덱싱 실패 (contentId={}): {}",
                            content.getId(), e.getMessage());
                } catch (Exception e) {
                    failCount++;
                    esSyncFailureService.record(content.getId(), e.getMessage());
                    log.error("[ES Sync] 영구 실패 의심 (contentId={}): {}",
                            content.getId(), e.getMessage());
                }
            }

            // 커서 전진: 마지막 항목의 updatedAt + id
            if (!chunk.isEmpty()) {
                Content last = chunk.get(chunk.size() - 1);
                watermark = last.getUpdatedAt();
                lastId = last.getId();
            }

        } while (chunk.size() == CHUNK_SIZE);

        // ── 3. 워터마크 저장 ─────────────────────────────────────────────
        if (successCount > 0 || failCount > 0) {
            saveWatermarkCursor(watermark, lastId);
            log.debug("[ES Sync] 완료. success={}, fail={}, watermark={}, lastId={}",
                    successCount, failCount, watermark, lastId);
        } else {
            // 처리할 콘텐츠가 없으면 현재 시각으로 전진, lastId 초기화
            saveWatermarkCursor(LocalDateTime.now(), 0L);
        }

        if (failCount > 0) {
            log.error("[ES Sync] 일부 실패 — DLQ에 저장됨. success={}, fail={}",
                    successCount, failCount);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  워터마크 3계층 폴백 — timestamp + lastId 커서 관리
    // ──────────────────────────────────────────────────────────────────────────

    private SchedulerWatermarkJdbcRepository.WatermarkCursor loadWatermarkCursor() {
        // 1차: Redis
        try {
            String stored = redisTemplate.opsForValue().get(WATERMARK_KEY);
            if (stored != null) {
                return parseRedisCursor(stored);
            }
        } catch (DataAccessException e) {
            log.warn("[ES Sync] Redis 워터마크 조회 실패 — MySQL 폴백 시도: {}", e.getMessage());
        }

        // 2차: MySQL
        try {
            return watermarkRepository.loadCursor(SCHEDULER_NAME)
                    .orElse(new SchedulerWatermarkJdbcRepository.WatermarkCursor(
                            lastSyncedAt, lastSyncedId));
        } catch (DataAccessException e) {
            log.warn("[ES Sync] MySQL 워터마크 조회도 실패 — 인메모리 폴백: {}", e.getMessage());
        }

        // 3차: 인메모리
        return new SchedulerWatermarkJdbcRepository.WatermarkCursor(lastSyncedAt, lastSyncedId);
    }

    private void saveWatermarkCursor(LocalDateTime watermark, Long lastId) {
        lastSyncedAt = watermark;
        lastSyncedId = lastId;

        // MySQL
        try {
            watermarkRepository.saveCursor(SCHEDULER_NAME, watermark, lastId);
        } catch (DataAccessException e) {
            log.warn("[ES Sync] MySQL 워터마크 저장 실패 (인메모리 유지): {}", e.getMessage());
        }

        // Redis — "2026-03-16T10:00:00.123|12345" 형태
        try {
            redisTemplate.opsForValue().set(WATERMARK_KEY,
                    watermark.toString() + "|" + lastId);
        } catch (DataAccessException e) {
            log.warn("[ES Sync] Redis 워터마크 저장 실패: {}", e.getMessage());
        }
    }

    /**
     * Redis 저장 형태 파싱: "2026-03-16T10:00:00.123|12345"
     * 하위 호환: 기존 timestamp만 저장된 경우 lastId=0으로 처리
     */
    private SchedulerWatermarkJdbcRepository.WatermarkCursor parseRedisCursor(String stored) {
        try {
            int sep = stored.lastIndexOf('|');
            if (sep > 0) {
                LocalDateTime ts = LocalDateTime.parse(stored.substring(0, sep));
                Long id = Long.parseLong(stored.substring(sep + 1));
                return new SchedulerWatermarkJdbcRepository.WatermarkCursor(ts, id);
            }
            // 하위 호환: 기존에 timestamp만 저장된 경우
            return new SchedulerWatermarkJdbcRepository.WatermarkCursor(
                    LocalDateTime.parse(stored), 0L);
        } catch (Exception e) {
            log.error("[ES Sync] Redis 워터마크 파싱 실패 — MySQL 폴백: stored='{}'", stored);
            throw e;
        }
    }
}