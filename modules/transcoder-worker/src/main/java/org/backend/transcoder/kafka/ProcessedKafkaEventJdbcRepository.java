package org.backend.transcoder.kafka;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Kafka 이벤트 중복 처리 방지를 위한 JDBC 레포지토리.
 *
 * <p>[멱등성 보장 흐름]
 * <pre>
 *   transcode()  @Transactional
 *     ├── isProcessed(eventId) → true  : 즉시 반환 (이미 처리된 이벤트)
 *     ├── ... FFmpeg, MinIO ...
 *     └── vf.updateTranscodeStatus(DONE)
 *     └── markProcessed(eventId, videoId)  ← DONE 업데이트와 동일 트랜잭션 커밋
 * </pre>
 *
 * <p>[트랜잭션 참여]
 //{VideoTranscodeService#transcode}의 {@code @Transactional} 안에서 호출되므로
 * JPA와 동일 JDBC 커넥션을 공유한다. REQUIRES_NEW 불필요.
 *
 * <p>[재시도 안전성]
 * 트랜스코딩 실패 → {@code @Transactional} 롤백 → {@code markProcessed} 롤백
 * → 다음 재시도에서 {@code isProcessed()} = false → 정상 재처리
 *
 * <p>[INSERT IGNORE]
 * PK(event_id) 중복 시 예외 없이 무시. 동시 중복 요청 방어.
 */
@Repository
@RequiredArgsConstructor
public class ProcessedKafkaEventJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final String EXISTS_SQL =
            "SELECT COUNT(*) FROM processed_kafka_events WHERE event_id = ?";

    private static final String INSERT_SQL =
            "INSERT IGNORE INTO processed_kafka_events (event_id, video_id, processed_at) " +
            "VALUES (?, ?, NOW(3))";

    /**
     * 이미 처리된 이벤트인지 확인한다.
     */
    public boolean isProcessed(String eventId) {
        Integer count = jdbcTemplate.queryForObject(EXISTS_SQL, Integer.class, eventId);
        return count != null && count > 0;
    }

    /**
     * 이벤트를 처리 완료로 기록한다.
     * 호출 시점의 트랜잭션에 참여한다.
     */
    public void markProcessed(String eventId, long videoId) {
        jdbcTemplate.update(INSERT_SQL, eventId, videoId);
    }
    public void markProcessed(String eventId) {
        jdbcTemplate.update(INSERT_SQL, eventId, -1L);
    }
}
