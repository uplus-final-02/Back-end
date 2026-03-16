package org.backend.userapi.kafka.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class UserVideoTranscodeOutboxJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final String INSERT_SQL =
            "INSERT INTO user_video_transcode_outbox (event_id, user_video_file_id, payload, created_at) " +
                    "VALUES (?, ?, ?, NOW(3))";

    private static final String SELECT_SQL =
            "SELECT id, event_id, user_video_file_id, payload " +
                    "FROM user_video_transcode_outbox " +
                    "ORDER BY created_at ASC " +
                    "LIMIT ?";

    private static final String DELETE_SQL =
            "DELETE FROM user_video_transcode_outbox WHERE id = ?";

    public void save(String eventId, long userVideoFileId, String payload) {
        jdbcTemplate.update(INSERT_SQL, eventId, userVideoFileId, payload);
    }

    public List<OutboxRow> findOldest(int limit) {
        return jdbcTemplate.query(
                SELECT_SQL,
                (rs, rowNum) -> new OutboxRow(
                        rs.getLong("id"),
                        rs.getString("event_id"),
                        rs.getLong("user_video_file_id"),
                        rs.getString("payload")
                ),
                limit
        );
    }

    public void deleteById(long id) {
        jdbcTemplate.update(DELETE_SQL, id);
    }

    public record OutboxRow(long id, String eventId, long userVideoFileId, String payload) {}
}