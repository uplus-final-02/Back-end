package org.backend.userapi.video.scheduler;

import content.repository.ContentRepository;
import content.repository.VideoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ViewCountFlushScheduler {

    private final StringRedisTemplate redisTemplate;
    private final VideoRepository videoRepository;
    private final ContentRepository contentRepository;

    // Redis Key Prefix
    private static final String CONTENT_VIEW_KEY_PREFIX = "content:view:";
    private static final String VIDEO_VIEW_KEY_PREFIX = "video:view:";

    /**
     * 3분마다 Redis에 쌓인 조회수를 DB에 반영(Flush)합니다.
     * cron: cron: "0 0/3 * * * *" (3분 간격으로 정각 0초에 실행. 예: 0분 0초, 3분 0초, 6분 0초...)
     */
    @Scheduled(cron = "0 0/3 * * * *")
    @Transactional
    public void flushViewCountsToDB() {
        log.info("[Scheduler] Redis 조회수 DB 동기화(Flush) 시작");
        long startTime = System.currentTimeMillis();

        // 1. 비디오 조회수 동기화
        int flushedVideoCount = flushCounts(VIDEO_VIEW_KEY_PREFIX, "Video");

        // 2. 컨텐츠(시리즈) 조회수 동기화
        int flushedContentCount = flushCounts(CONTENT_VIEW_KEY_PREFIX, "Content");

        long endTime = System.currentTimeMillis();
        log.info("[Scheduler] Redis 조회수 DB 동기화 완료. (Video: {}건, Content: {}건 반영, 소요시간: {}ms)",
            flushedVideoCount, flushedContentCount, (endTime - startTime));
    }

    /**
     * 특정 프리픽스를 가진 Redis 키를 찾아 DB에 업데이트하고 Redis에서 값을 초기화(0)합니다.
     */
    private int flushCounts(String keyPrefix, String type) {
        Map<Long, Long> deltaMap = new HashMap<>();

        // 1. SCAN을 사용하여 해당하는 모든 키를 안전하게 검색 (KEYS 명령어 사용 금지)
        ScanOptions options = ScanOptions.scanOptions().match(keyPrefix + "*").count(100).build();
        try (RedisConnection connection = redisTemplate.getConnectionFactory().getConnection();
             Cursor<byte[]> cursor = connection.scan(options)) {

            while (cursor.hasNext()) {
                String key = new String(cursor.next());
                Long entityId = extractIdFromKey(key, keyPrefix);

                if (entityId != null) {
                    // 원자적(Atomic)으로 값을 읽고 동시에 '0'으로 리셋 (GetAndSet)
                    // 이 사이에 증가한 조회수 유실을 방지합니다.
                    String value = redisTemplate.opsForValue().getAndSet(key, "0");
                    if (value != null) {
                        long delta = Long.parseLong(value);
                        if (delta > 0) {
                            deltaMap.put(entityId, delta);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("[Scheduler] Redis SCAN 중 오류 발생 (Type: {})", type, e);
            return 0;
        }

        if (deltaMap.isEmpty()) {
            return 0;
        }

        // 2. DB 업데이트 수행
        int updateCount = 0;
        for (Map.Entry<Long, Long> entry : deltaMap.entrySet()) {
            Long id = entry.getKey();
            Long viewCountDelta = entry.getValue();

            try {
                if ("Video".equals(type)) {
                    // DB 락을 최소화하기 위해 객체를 조회하지 않고 Native Query(또는 JPQL)로 직접 UPDATE
                    videoRepository.incrementViewCount(id, viewCountDelta);
                } else if ("Content".equals(type)) {
                    contentRepository.incrementViewCount(id, viewCountDelta);
                }
                updateCount++;
            } catch (Exception e) {
                log.error("[Scheduler] DB 조회수 업데이트 실패 (Type: {}, ID: {}, Delta: {})", type, id, viewCountDelta, e);
                // 업데이트에 실패한 값은 다시 Redis에 복구(보상 트랜잭션)하여 다음 주기에 반영되도록 처리
                try {
                    redisTemplate.opsForValue().increment(keyPrefix + id, viewCountDelta);
                    log.warn("[Scheduler] 보상 트랜잭션 완료 - 다음 사이클에 재시도: type={}, id={}", type, id);
                } catch (Exception redisEx) {
                    // Redis도 다운이면 이 delta는 손실 — 로그만 남김
                    log.error("[Scheduler] 보상 트랜잭션 실패 (Redis 다운) - 조회수 {}건 손실 가능: type={}, id={}", viewCountDelta, type, id);
                }
            }
        }

        return updateCount;
    }

    private Long extractIdFromKey(String key, String prefix) {
        try {
            return Long.parseLong(key.substring(prefix.length()));
        } catch (NumberFormatException e) {
            log.warn("올바르지 않은 형식의 키 발견: {}", key);
            return null;
        }
    }
}
