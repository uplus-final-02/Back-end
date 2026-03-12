package org.backend.admin.kafka.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Outbox 행을 저장/조회/삭제하는 JDBC 레포지토리.
 *
 * <p>[JPA 트랜잭션 참여]
 * {@code AdminVideoUploadService.confirmUpload()}의 {@code @Transactional(JPA)} 안에서
 * {@link #save}를 호출하면, Spring {@code JpaTransactionManager}가 관리하는
 * 동일 JDBC 커넥션을 공유하므로 JPA 변경과 outbox INSERT가 하나의 트랜잭션으로 커밋된다.
 */
@Repository
@RequiredArgsConstructor
public class VideoTranscodeOutboxJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final String INSERT_SQL =
            "INSERT INTO video_transcode_outbox (event_id, video_file_id, payload, created_at) " +
            "VALUES (?, ?, ?, NOW(3))";

    private static final String SELECT_SQL =
            "SELECT id, event_id, video_file_id, payload " +
            "FROM video_transcode_outbox " +
            "ORDER BY created_at ASC " +
            "LIMIT ?";

    private static final String DELETE_SQL =
            "DELETE FROM video_transcode_outbox WHERE id = ?";

    /**
     * Outbox 행 삽입. 호출 시점의 트랜잭션에 참여한다.
     */
    public void save(String eventId, long videoFileId, String payload) {
        jdbcTemplate.update(INSERT_SQL, eventId, videoFileId, payload);
    }

    /**
     * 생성 시각 오름차순으로 미발행 행을 조회한다.
     *
     * @param limit 최대 조회 건수
     */
    public List<OutboxRow> findOldest(int limit) {
        return jdbcTemplate.query(
                SELECT_SQL,
                (rs, rowNum) -> new OutboxRow(
                        rs.getLong("id"),
                        rs.getString("event_id"),
                        rs.getLong("video_file_id"),
                        rs.getString("payload")
                ),
                limit
        );
    }

    /**
     * Kafka 발행 확인 후 outbox 행 제거.
     */
    public void deleteById(long id) {
        jdbcTemplate.update(DELETE_SQL, id);
    }

    /**
     * 폴링 스케줄러가 사용하는 읽기 전용 데이터 객체.
     */
    public record OutboxRow(long id, String eventId, long videoFileId, String payload) {}
}
