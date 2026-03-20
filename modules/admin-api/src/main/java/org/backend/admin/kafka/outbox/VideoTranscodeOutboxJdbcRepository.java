package org.backend.admin.kafka.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class VideoTranscodeOutboxJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final String INSERT_SQL =
            "INSERT INTO video_transcode_outbox (event_id, target_type, target_id, topic, payload, created_at) " +
                    "VALUES (?, ?, ?, ?, ?, NOW(3))";

    private static final String SELECT_SQL =
            "SELECT id, event_id, target_type, target_id, topic, payload " +
                    "FROM video_transcode_outbox " +
                    "ORDER BY created_at ASC " +
                    "LIMIT ?";

    private static final String DELETE_SQL =
            "DELETE FROM video_transcode_outbox WHERE id = ?";

    public void save(String eventId, String targetType, long targetId, String topic, String payload) {
        jdbcTemplate.update(INSERT_SQL, eventId, targetType, targetId, topic, payload);
    }

    public List<OutboxRow> findOldest(int limit) {
        return jdbcTemplate.query(
                SELECT_SQL,
                (rs, rowNum) -> new OutboxRow(
                        rs.getLong("id"),
                        rs.getString("event_id"),
                        rs.getString("target_type"),
                        rs.getLong("target_id"),
                        rs.getString("topic"),
                        rs.getString("payload")
                ),
                limit
        );
    }

    public void deleteById(long id) {
        jdbcTemplate.update(DELETE_SQL, id);
    }

    public record OutboxRow(
            long id,
            String eventId,
            String targetType,
            long targetId,
            String topic,
            String payload
    ) {}
}