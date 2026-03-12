package org.backend.transcoder.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

/**
 * Kafka 이벤트 중복 처리 방지 — JDBC 기반 멱등성 저장소.
 *
 * <p>processed_kafka_events 테이블에 처리 완료된 eventId를 저장하여
 * 동일 이벤트의 재처리를 방지합니다 (Kafka at-least-once → exactly-once 보장).
 *
 * <p>JPA 엔티티 없이 {@link JdbcTemplate}을 직접 사용하는 이유:
 * <ul>
 *   <li>단순 INSERT/SELECT 2개 쿼리만 필요 — 영속성 컨텍스트 오버헤드 불필요</li>
 *   <li>INSERT IGNORE로 동시 중복 실행 시 DB 레벨 원자적 중복 방지 보장</li>
 * </ul>
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ProcessedKafkaEventJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 이미 처리된 이벤트인지 확인.
     *
     * @param eventId Kafka 이벤트 UUID
     * @return true면 중복 이벤트 → 재처리 생략
     */
    public boolean isProcessed(String eventId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM processed_kafka_events WHERE event_id = ?",
                Integer.class,
                eventId
        );
        return count != null && count > 0;
    }

    /**
     * 이벤트 처리 완료 마킹.
     * INSERT IGNORE → 동시 중복 실행 시 두 번째 INSERT는 조용히 무시됨.
     *
     * @param eventId     Kafka 이벤트 UUID
     * @param videoFileId 처리된 VideoFile ID
     */
    public void markProcessed(String eventId, Long videoFileId) {
        jdbcTemplate.update(
                "INSERT IGNORE INTO processed_kafka_events (event_id, video_file_id, processed_at) VALUES (?, ?, ?)",
                eventId, videoFileId, LocalDateTime.now()
        );
    }
}
