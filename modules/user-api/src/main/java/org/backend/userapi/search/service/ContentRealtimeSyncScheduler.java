package org.backend.userapi.search.service;

import content.entity.Content;
import content.repository.ContentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.backend.userapi.common.scheduler.SchedulerWatermarkJdbcRepository;
import org.backend.userapi.search.service.EsSyncFailureService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

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
    public void syncUpdatedContents() {
        if (!realtimeSyncEnabled) {
            return;
        }

        // ── 1. 워터마크 로드 (Redis → MySQL → 인메모리) ──────────────────
        LocalDateTime watermark = loadWatermark();
        LocalDateTime newWatermark = watermark; // 💡 청크 내 max 추적용

        int successCount = 0;
        int failCount = 0;

        // [피드백 반영] Slice 기반 배치 처리 — 워터마크가 크게 뒤처져도 OOM 방지
        // updatedAt ASC 정렬로 offset 기반 페이징 안정화
        Pageable chunkPageable = PageRequest.of(0, 500,
                Sort.by(Sort.Direction.ASC, "updatedAt"));
        Slice<Content> slice;

        do {
            slice = contentRepository.findByUpdatedAtGreaterThanEqual(watermark, chunkPageable);

            for (Content content : slice.getContent()) {
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

                // 💡 청크 내 max(updatedAt) 추적
                if (content.getUpdatedAt() != null
                        && content.getUpdatedAt().isAfter(newWatermark)) {
                    newWatermark = content.getUpdatedAt();
                }
            }

            chunkPageable = chunkPageable.next();

        } while (slice.hasNext());

        // ── 2. 워터마크 저장 (MySQL → Redis → 인메모리) ──────────────────
        // [피드백 반영] Poison Pill 방어 — 실패 있어도 워터마크 무조건 전진 (무한 루프 방지)
        if (newWatermark.isAfter(watermark)) {
            saveWatermark(newWatermark);
            log.debug("[ES Sync] 완료. success={}, fail={}, watermark={}",
                    successCount, failCount, newWatermark);
        } else {
            // 처리할 콘텐츠가 없었던 경우 현재 시각으로 전진
            saveWatermark(LocalDateTime.now());
        }

        if (failCount > 0) {
            log.error("[ES Sync] 일부 실패 — DLQ에 저장됨. success={}, fail={}",
                    successCount, failCount);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  워터마크 3계층 폴백 (기존 로직 유지)
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