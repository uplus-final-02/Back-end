package org.backend.userapi.common.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 스케줄러 워터마크를 MySQL에 저장/조회하는 JDBC 리포지토리.
 *
 * <p>[역할]
 * Redis 장애 시 폴백 워터마크 저장소.
 * Redis가 정상이면 이 저장소는 write-through(백업 역할)로만 동작.
 *
 * <p>[트랜잭션 설계]
 * <ul>
 *   <li>{@link #save}: {@code REQUIRES_NEW} — 외부 {@code @Transactional(readOnly=true)} 와
 *       독립적인 쓰기 트랜잭션을 별도로 열어 커밋. readOnly 트랜잭션 안에서 INSERT/UPDATE 실패 방지.
 *   <li>{@link #load}: 트랜잭션 없음 — SELECT이므로 외부 readOnly 트랜잭션에 참여해도 무방.
 * </ul>
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class SchedulerWatermarkJdbcRepository {

    private static final String LOAD_SQL =
            "SELECT watermark FROM scheduler_watermark WHERE scheduler_name = ?";

    private static final String SAVE_SQL =
            "INSERT INTO scheduler_watermark (scheduler_name, watermark, updated_at) " +
            "VALUES (?, ?, NOW(3)) " +
            "ON DUPLICATE KEY UPDATE watermark = VALUES(watermark), updated_at = NOW(3)";
    
    private static final String LOAD_CURSOR_SQL =
            "SELECT watermark, last_id FROM scheduler_watermark WHERE scheduler_name = ?";
    private static final String SAVE_CURSOR_SQL =
            "INSERT INTO scheduler_watermark (scheduler_name, watermark, last_id, updated_at) " +
            "VALUES (?, ?, ?, NOW(3)) " +
            "ON DUPLICATE KEY UPDATE watermark = VALUES(watermark), last_id = VALUES(last_id), updated_at = NOW(3)";

    private final JdbcTemplate jdbcTemplate;

    /**
     * 워터마크를 MySQL에서 조회.
     * 행이 없으면 {@link Optional#empty()} 반환.
     *
     * <p>[DATETIME 파싱 주의]
     * {@code queryForObject(..., String.class)}로 읽으면 MySQL JDBC 드라이버가
     * {@code "2026-03-10 14:30:00.123"} 형식(공백 구분자)으로 반환하여
     * {@link java.time.format.DateTimeParseException}이 발생한다.
     * {@code Timestamp.class}로 읽어 {@link Timestamp#toLocalDateTime()}으로 변환하면
     * JDBC 드라이버가 내부적으로 정확히 처리한다.
     */
    public Optional<LocalDateTime> load(String schedulerName) {
        try {
            Timestamp result = jdbcTemplate.queryForObject(LOAD_SQL, Timestamp.class, schedulerName);
            if (result == null) return Optional.empty();
            return Optional.of(result.toLocalDateTime());
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();   // 최초 실행 시 행 없음 → 정상
        }
    }
    
    public Optional<WatermarkCursor> loadCursor(String schedulerName) {
        try {
            return Optional.ofNullable(
                jdbcTemplate.queryForObject(LOAD_CURSOR_SQL, (rs, rowNum) -> {
                    Timestamp ts = rs.getTimestamp("watermark");
                    long lastId = rs.getLong("last_id");
                    if (ts == null) return null;
                    return new WatermarkCursor(ts.toLocalDateTime(), lastId);
                }, schedulerName)
            );
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

   
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveCursor(String schedulerName, LocalDateTime watermark, Long lastId) {
        jdbcTemplate.update(SAVE_CURSOR_SQL, schedulerName,
                Timestamp.valueOf(watermark), lastId != null ? lastId : 0L);
    }
    
    public record WatermarkCursor(LocalDateTime watermark, Long lastId) {}

    /**
     * 워터마크를 MySQL에 저장(UPSERT).
     *
     * <p>{@code REQUIRES_NEW}: 호출 시점에 활성화된 readOnly 트랜잭션을 잠시 중단하고
     * 새로운 쓰기 트랜잭션을 독립적으로 열어 커밋 후 원래 트랜잭션 재개.
     *
     * <p>[타입 바인딩]
     * {@code watermark.toString()}은 ISO-8601 'T' 구분자 형식으로 문자열을 넘기므로
     * DATETIME 컬럼 바인딩이 DB/드라이버에 따라 불안정할 수 있다.
     * {@link Timestamp#valueOf(LocalDateTime)}으로 변환해 올바른 JDBC 타입으로 바인딩한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(String schedulerName, LocalDateTime watermark) {
        jdbcTemplate.update(SAVE_SQL, schedulerName, Timestamp.valueOf(watermark));
    }
}
