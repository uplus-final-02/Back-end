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

import content.entity.UserContent;
import content.repository.UserContentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * user_contents 테이블 변경을 감지하여 ES(user_contents_v1)에 동기화하는 스케줄러.
 * ContentRealtimeSyncScheduler와 동일한 패턴 (커서 기반 + 3계층 워터마크 폴백).
 *
 * <p>ACTIVE → ES 인덱싱, HIDDEN/DELETED → ES에서 삭제.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserContentRealtimeSyncScheduler {

    private static final String WATERMARK_KEY  = "scheduler:es-user-sync:watermark";
    private static final String SCHEDULER_NAME = "esUserSyncTask";
    private static final int CHUNK_SIZE = 500;

    private final UserContentRepository userContentRepository;
    private final UserContentIndexingService userContentIndexingService;
    private final StringRedisTemplate redisTemplate;
    private final SchedulerWatermarkJdbcRepository watermarkRepository;

    @Value("${app.search.realtime-sync.enabled:true}")
    private boolean realtimeSyncEnabled;

    private LocalDateTime lastSyncedAt = LocalDateTime.now().minusMinutes(5);
    private Long lastSyncedId = 0L;

    @Scheduled(fixedDelayString = "${app.search.realtime-sync.interval-ms:30000}")
    @SchedulerLock(
            name           = "esUserSyncTask",
            lockAtMostFor  = "PT10M",
            lockAtLeastFor = "PT5S"
    )
    public void syncUpdatedUserContents() {
        if (!realtimeSyncEnabled) return;

        SchedulerWatermarkJdbcRepository.WatermarkCursor cursor = loadWatermarkCursor();
        LocalDateTime watermark = cursor.watermark();
        Long lastId = cursor.lastId();

        int successCount = 0;
        int failCount = 0;

        List<UserContent> chunk;
        do {
            chunk = userContentRepository.findUpdatedAfterCursor(
                    watermark, lastId, PageRequest.of(0, CHUNK_SIZE));

            for (UserContent uc : chunk) {
                try {
                    if (uc.getContentStatus() == common.enums.ContentStatus.ACTIVE) {
                        userContentIndexingService.indexUserContent(uc.getId());
                    } else {
                        userContentIndexingService.deleteUserContent(uc.getId());
                    }
                    successCount++;
                } catch (Exception e) {
                    failCount++;
                    log.warn("[UserContent ES Sync] 인덱싱 실패 (userContentId={}): {}",
                            uc.getId(), e.getMessage());
                }
            }

            if (!chunk.isEmpty()) {
                UserContent last = chunk.get(chunk.size() - 1);
                watermark = last.getUpdatedAt();
                lastId = last.getId();
            }

        } while (chunk.size() == CHUNK_SIZE);

        if (successCount > 0 || failCount > 0) {
            saveWatermarkCursor(watermark, lastId);
            log.debug("[UserContent ES Sync] 완료. success={}, fail={}, watermark={}, lastId={}",
                    successCount, failCount, watermark, lastId);
        } else {
            saveWatermarkCursor(LocalDateTime.now(), 0L);
        }

        if (failCount > 0) {
            log.error("[UserContent ES Sync] 일부 실패. success={}, fail={}",
                    successCount, failCount);
        }
    }

    // ── 워터마크 3계층 폴백 ──────────────────────────────────────

    private SchedulerWatermarkJdbcRepository.WatermarkCursor loadWatermarkCursor() {
        try {
            String stored = redisTemplate.opsForValue().get(WATERMARK_KEY);
            if (stored != null) {
                return parseRedisCursor(stored);
            }
        } catch (Exception e) {
            log.warn("[UserContent ES Sync] Redis 워터마크 조회/파싱 실패 — MySQL 폴백: {}",
                    e.getMessage());
        }

        try {
            return watermarkRepository.loadCursor(SCHEDULER_NAME)
                    .orElse(new SchedulerWatermarkJdbcRepository.WatermarkCursor(
                            lastSyncedAt, lastSyncedId));
        } catch (DataAccessException e) {
            log.warn("[UserContent ES Sync] MySQL 워터마크 조회 실패 — 인메모리 폴백: {}",
                    e.getMessage());
        }

        return new SchedulerWatermarkJdbcRepository.WatermarkCursor(lastSyncedAt, lastSyncedId);
    }

    private void saveWatermarkCursor(LocalDateTime watermark, Long lastId) {
        lastSyncedAt = watermark;
        lastSyncedId = lastId;

        try {
            watermarkRepository.saveCursor(SCHEDULER_NAME, watermark, lastId);
        } catch (DataAccessException e) {
            log.warn("[UserContent ES Sync] MySQL 워터마크 저장 실패: {}", e.getMessage());
        }

        try {
            redisTemplate.opsForValue().set(WATERMARK_KEY,
                    watermark.toString() + "|" + lastId);
        } catch (DataAccessException e) {
            log.warn("[UserContent ES Sync] Redis 워터마크 저장 실패: {}", e.getMessage());
        }
    }

    private SchedulerWatermarkJdbcRepository.WatermarkCursor parseRedisCursor(String stored) {
        int sep = stored.lastIndexOf('|');
        if (sep > 0) {
            LocalDateTime ts = LocalDateTime.parse(stored.substring(0, sep));
            Long id = Long.parseLong(stored.substring(sep + 1));
            return new SchedulerWatermarkJdbcRepository.WatermarkCursor(ts, id);
        }
        return new SchedulerWatermarkJdbcRepository.WatermarkCursor(
                LocalDateTime.parse(stored), 0L);
    }
}