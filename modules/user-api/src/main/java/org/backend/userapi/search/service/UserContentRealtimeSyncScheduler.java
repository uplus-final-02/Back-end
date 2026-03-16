package org.backend.userapi.search.service;

import content.entity.UserContent;
import content.repository.UserContentRepository;
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
 * 유저 업로드 콘텐츠 실시간 ES 동기화 스케줄러.
 *
 * <p>{@link ContentRealtimeSyncScheduler}와 동일한 3계층 워터마크 폴백 전략을 사용한다.
 * <pre>
 *   1. Redis  (빠른 읽기/쓰기, 정상 경로)
 *      ↓ Redis 장애 or 파싱 오류
 *   2. MySQL  (scheduler_watermark 테이블, REQUIRES_NEW 트랜잭션)
 *      ↓ MySQL도 장애
 *   3. 인메모리 lastSyncedAt  (중복 인덱싱 가능하지만 데이터 손상 없음)
 * </pre>
 *
 * <p>워터마크 키와 스케줄러 이름을 관리자 콘텐츠 스케줄러와 분리해
 * 각 동기화 진행이 서로 영향을 주지 않도록 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserContentRealtimeSyncScheduler {

    private static final String WATERMARK_KEY  = "scheduler:es-sync:user-content:watermark";
    private static final String SCHEDULER_NAME = "userContentEsSyncTask";

    private final UserContentRepository userContentRepository;
    private final UserContentIndexingService userContentIndexingService;
    private final StringRedisTemplate redisTemplate;
    private final SchedulerWatermarkJdbcRepository watermarkRepository;

    @Value("${app.search.realtime-sync.enabled:true}")
    private boolean realtimeSyncEnabled;

    /** 인메모리 폴백 워터마크 (3계층 중 최후 수단) */
    private LocalDateTime lastSyncedAt = LocalDateTime.now().minusMinutes(5);

    @Scheduled(fixedDelayString = "${app.search.realtime-sync.interval-ms:30000}")
    @SchedulerLock(
            name           = "userContentEsSyncTask",
            lockAtMostFor  = "PT10M",
            lockAtLeastFor = "PT5S"
    )
    @Transactional(readOnly = true)
    public void syncUpdatedUserContents() {
        if (!realtimeSyncEnabled) {
            return;
        }

        // 1. 워터마크 로드 (Redis → MySQL → 인메모리)
        LocalDateTime watermark = loadWatermark();

        List<UserContent> updatedContents = userContentRepository.findByUpdatedAtAfter(watermark);
        if (updatedContents.isEmpty()) {
            saveWatermark(LocalDateTime.now());
            return;
        }

        int successCount = 0;
        int failCount    = 0;

        for (UserContent uc : updatedContents) {
            try {
                if (uc.getContentStatus().name().equals("DELETED")) {
                    // DELETED 전환 시 ES에서 제거
                    userContentIndexingService.deleteUserContent(uc.getId());
                } else {
                    userContentIndexingService.indexUserContent(uc.getId());
                }
                successCount++;
            } catch (DataAccessException e) {
                failCount++;
                log.warn("[UserContent ES Sync] 인덱싱 실패 - ES 연결 문제 (userContentId={}): {}",
                        uc.getId(), e.getMessage());
            } catch (Exception e) {
                failCount++;
                log.warn("[UserContent ES Sync] 인덱싱 실패 - 알 수 없는 오류 (userContentId={}): {}",
                        uc.getId(), e.getMessage());
            }
        }

        if (failCount > 0) {
            // 실패가 1건이라도 있으면 워터마크 전진 안 함 → 다음 주기에 재시도
            log.warn("[UserContent ES Sync] 부분 실패. success={}, fail={}, watermark 유지={}",
                    successCount, failCount, watermark);
        } else {
            LocalDateTime newWatermark = updatedContents.stream()
                    .map(UserContent::getUpdatedAt)
                    .filter(t -> t != null)
                    .max(LocalDateTime::compareTo)
                    .orElse(LocalDateTime.now());

            saveWatermark(newWatermark);
            log.debug("[UserContent ES Sync] 완료. syncedCount={}, watermark={}",
                    successCount, newWatermark);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  워터마크 3계층 폴백
    // ──────────────────────────────────────────────────────────────────────────

    private LocalDateTime loadWatermark() {
        String stored = null;
        try {
            stored = redisTemplate.opsForValue().get(WATERMARK_KEY);
        } catch (DataAccessException e) {
            log.warn("[UserContent ES Sync] Redis 워터마크 조회 실패 — MySQL 폴백: {}", e.getMessage());
        }

        if (stored != null) {
            try {
                return LocalDateTime.parse(stored);
            } catch (DateTimeParseException e) {
                log.error("[UserContent ES Sync] Redis 워터마크 파싱 실패 — MySQL 폴백: stored='{}', error={}",
                        stored, e.getMessage());
            }
        }

        try {
            return watermarkRepository.load(SCHEDULER_NAME).orElse(lastSyncedAt);
        } catch (DataAccessException e) {
            log.warn("[UserContent ES Sync] MySQL 워터마크 조회도 실패 — 인메모리 폴백: {}", e.getMessage());
        }

        return lastSyncedAt;
    }

    private void saveWatermark(LocalDateTime newWatermark) {
        lastSyncedAt = newWatermark;

        try {
            watermarkRepository.save(SCHEDULER_NAME, newWatermark);
        } catch (DataAccessException e) {
            log.warn("[UserContent ES Sync] MySQL 워터마크 저장 실패 (인메모리 유지): {}", e.getMessage());
        }

        try {
            redisTemplate.opsForValue().set(WATERMARK_KEY, newWatermark.toString());
        } catch (DataAccessException e) {
            log.warn("[UserContent ES Sync] Redis 워터마크 저장 실패 — MySQL이 소스 오브 트루스: {}", e.getMessage());
        }
    }
}
