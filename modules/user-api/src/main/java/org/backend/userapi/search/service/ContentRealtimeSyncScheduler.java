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

// 💡 피드백 반영: @Transactional(readOnly = true) 제거 (불필요한 DB 커넥션 점유 방지)
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * DB에서 변경된 콘텐츠를 주기적으로 Elasticsearch에 인덱싱하는 실시간 동기화 스케줄러.
 *
 * <p>[워터마크 공유 전략 — 3계층 폴백]
 * <pre>
 * 1. Redis  (빠른 읽기/쓰기, 정상 경로)
 * ↓ Redis 장애 or 파싱 오류
 * 2. MySQL  (scheduler_watermark 테이블, REQUIRES_NEW 트랜잭션)
 * Redis down + 인스턴스 교대 시에도 워터마크 일관성 보장
 * ↓ MySQL도 장애
 * 3. 인메모리 lastSyncedAt  (중복 인덱싱 가능하지만 데이터 손상 없음)
 * </pre>
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
    private final EsSyncFailureService esSyncFailureService;

    @Value("${app.search.realtime-sync.enabled:true}")
    private boolean realtimeSyncEnabled;

    private LocalDateTime lastSyncedAt = LocalDateTime.now().minusMinutes(5);

    @Scheduled(fixedDelayString = "${app.search.realtime-sync.interval-ms:30000}")
    @SchedulerLock(
            name           = "esSyncTask",
            lockAtMostFor  = "PT10M", 
            lockAtLeastFor = "PT5S"   
    )
    // 💡 스케줄러 레벨의 트랜잭션 제거 완료
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
        // 💡 피드백 반영: 영구 실패 추적을 위한 리스트 (Log-based DLQ)
        List<Long> failedIds = new ArrayList<>();

        for (Content content : updatedContents) {
            try {
                contentIndexingService.indexContent(content.getId());
                successCount++;
            } catch (DataAccessException e) {
                failCount++;
                failedIds.add(content.getId());
                esSyncFailureService.record(content.getId(), e.getMessage()); // 💡 로그 대신 DB 저장
                log.warn("[ES Sync] ES 연결 문제로 인덱싱 실패 (contentId={})", content.getId(), e);
            } catch (Exception e) {
                failCount++;
                failedIds.add(content.getId());
                esSyncFailureService.record(content.getId(), e.getMessage()); // 💡 로그 대신 DB 저장
                log.error("[ES Sync] 영구 실패 의심 (contentId={})", content.getId(), e);
            }
        }

        // 💡 피드백 반영: Poison Pill 방어 전략
        // 실패가 있더라도 워터마크는 무조건 가장 최신 시간으로 전진시켜서 무한 루프를 방지합니다.
        LocalDateTime newWatermark = updatedContents.stream()
                .map(Content::getUpdatedAt)
                .filter(updatedAt -> updatedAt != null)
                .max(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now());

        if (failCount > 0) {
            // 실패한 ID들을 ERROR 로그로 남겨 추후 배치나 수동으로 복구할 수 있게 유도 (Dead Letter)
            log.error("[ES Sync] 실시간 동기화 중 일부 실패 (DLQ 로깅). success={}, fail={}, failedIds={}. 시스템 마비를 막기 위해 워터마크는 강제 전진합니다.",
                    successCount, failCount, failedIds);
        } else {
            log.debug("[ES Sync] 실시간 동기화 완료. syncedCount={}, watermark={}",
                    successCount, newWatermark);
        }

        // ── 2. 워터마크 저장 (MySQL → Redis → 인메모리) ──────────────
        saveWatermark(newWatermark);
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  워터마크 3계층 폴백 (기존의 훌륭한 로직 유지)
    // ──────────────────────────────────────────────────────────────────────────

    private LocalDateTime loadWatermark() {
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
                log.error("[ES Sync] Redis 워터마크 파싱 실패 (형식 오류, 코드 확인 필요) " +
                          "— MySQL 폴백 시도: stored='{}', error={}", stored, e.getMessage());
            }
        }

        try {
            return watermarkRepository.load(SCHEDULER_NAME).orElse(lastSyncedAt);
        } catch (DataAccessException e) {
            log.warn("[ES Sync] MySQL 워터마크 조회도 실패 — 인메모리 폴백: {}", e.getMessage());
        }

        return lastSyncedAt;
    }

    private void saveWatermark(LocalDateTime newWatermark) {
        lastSyncedAt = newWatermark;

        try {
            watermarkRepository.save(SCHEDULER_NAME, newWatermark);
        } catch (DataAccessException e) {
            log.warn("[ES Sync] MySQL 워터마크 저장 실패 (인메모리 유지): {}", e.getMessage());
        }

        try {
            redisTemplate.opsForValue().set(WATERMARK_KEY, newWatermark.toString());
        } catch (DataAccessException e) {
            log.warn("[ES Sync] Redis 워터마크 저장 실패 — MySQL이 소스 오브 트루스로 동작 중: {}", e.getMessage());
        }
    }
}