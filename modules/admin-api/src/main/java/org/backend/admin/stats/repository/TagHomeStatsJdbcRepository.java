package org.backend.admin.stats.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.backend.admin.stats.dto.AdminHomeTagStatsResponse;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class TagHomeStatsJdbcRepository {

	private final JdbcTemplate jdbcTemplate;
	
	/**
     * 홈 노출 태그(priority=1) 전체 기준 집계 데이터 조회
     * - 시작점: tags(priority=1, is_active=1)
     * - 연결 콘텐츠 없으면 0 처리
     * - ACTIVE 콘텐츠만 집계
     * - watch_histories.deleted_at IS NULL 만 집계
     */
	private static final String AGGREGATE_SQL = """
		    SELECT
		        t.tag_id,
		        ? AS stat_date,

		        COALESCE(base.total_view_count, 0) AS total_view_count,
		        COALESCE(base.total_bookmark_count, 0) AS total_bookmark_count,
		        COALESCE(w.total_watch_count, 0) AS total_watch_count,
		        COALESCE(w.completed_watch_count, 0) AS completed_watch_count,

		        CASE
		            WHEN COALESCE(base.total_view_count, 0) = 0 THEN 0
		            ELSE ROUND(base.total_bookmark_count * 1.0 / base.total_view_count, 4)
		        END AS bookmark_rate,

		        CASE
		            WHEN COALESCE(w.total_watch_count, 0) = 0 THEN 0
		            ELSE ROUND(w.completed_watch_count * 1.0 / w.total_watch_count, 4)
		        END AS completion_rate

		    FROM tags t

		    LEFT JOIN (
		        SELECT
		            ct.tag_id,
		            SUM(c.total_view_count) AS total_view_count,
		            SUM(c.bookmark_count) AS total_bookmark_count
		        FROM content_tags ct
		        JOIN contents c
		          ON c.content_id = ct.content_id
		         AND c.status = 'ACTIVE'
		        GROUP BY ct.tag_id
		    ) base
		        ON base.tag_id = t.tag_id

		    LEFT JOIN (
		        SELECT
		            ct.tag_id,
		            COUNT(wh.history_id) AS total_watch_count,
		            SUM(CASE WHEN wh.status = 'COMPLETED' THEN 1 ELSE 0 END) AS completed_watch_count
		        FROM content_tags ct
		        JOIN contents c
		          ON c.content_id = ct.content_id
		         AND c.status = 'ACTIVE'
		        JOIN watch_histories wh
		          ON wh.content_id = c.content_id
		         AND wh.deleted_at IS NULL
		        GROUP BY ct.tag_id
		    ) w
		        ON w.tag_id = t.tag_id

		    WHERE t.priority = 1
		      AND t.is_active = 1

		    ORDER BY t.tag_id
		    """;
	
    
    /**
     * 통계 UPSERT
     */
	private static final String UPSERT_SQL = """
	        INSERT INTO tag_home_stats
	        (
	            stat_date,
	            tag_id,
	            total_view_count,
	            total_bookmark_count,
	            total_watch_count,
	            completed_watch_count,
	            bookmark_rate,
	            completion_rate
	        )
	        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
	        ON DUPLICATE KEY UPDATE
	            total_view_count = VALUES(total_view_count),
	            total_bookmark_count = VALUES(total_bookmark_count),
	            total_watch_count = VALUES(total_watch_count),
	            completed_watch_count = VALUES(completed_watch_count),
	            bookmark_rate = VALUES(bookmark_rate),
	            completion_rate = VALUES(completion_rate),
	            updated_at = NOW()
	        """;
    
	// 집계 데이터 조회
	public List<TagHomeStatsRow> findDailyStatsRows(LocalDate statDate) {
        return jdbcTemplate.query(
                AGGREGATE_SQL,
                (rs, rowNum) -> new TagHomeStatsRow(
                        statDate,
                        rs.getLong("tag_id"),
                        rs.getLong("total_view_count"),
                        rs.getLong("total_bookmark_count"),
                        rs.getBigDecimal("bookmark_rate"),
                        rs.getLong("total_watch_count"),
                        rs.getLong("completed_watch_count"),
                        rs.getBigDecimal("completion_rate")
                ),
                Date.valueOf(statDate)
        );
    }
	
	/**
     * 통계 batch upsert
     */
	public int upsert(List<TagHomeStatsRow> rows) {
        int[] results = jdbcTemplate.batchUpdate(
                UPSERT_SQL,
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        TagHomeStatsRow row = rows.get(i);

                        ps.setDate(1, Date.valueOf(row.statDate()));
                        ps.setLong(2, row.tagId());
                        ps.setLong(3, row.totalViewCount());
                        ps.setLong(4, row.totalBookmarkCount());
                        ps.setLong(5, row.totalWatchCount());
                        ps.setLong(6, row.completedWatchCount());
                        ps.setBigDecimal(7, row.bookmarkRate());
                        ps.setBigDecimal(8, row.completionRate());
                    }

                    @Override
                    public int getBatchSize() {
                        return rows.size();
                    }
                }
        );

        return results.length;
    }
	
	public record TagHomeStatsRow(
            LocalDate statDate,
            Long tagId,
            Long totalViewCount,
            Long totalBookmarkCount,
            BigDecimal bookmarkRate,
            Long totalWatchCount,
            Long completedWatchCount,
            BigDecimal completionRate
    ) {
    }
	
	/*
	 *  통계 조회
	 */
	public LocalDate findLatestStatDate() {
        Date result = jdbcTemplate.queryForObject(
                "SELECT MAX(stat_date) FROM tag_home_stats",
                Date.class
        );
        return result != null ? result.toLocalDate() : null;
    }

    public List<AdminHomeTagStatsResponse> findByStatDate(LocalDate statDate) {
        String sql = """
            SELECT
                s.stat_date,
                s.tag_id,
                t.name AS tag_name,
                s.total_view_count,
                s.total_bookmark_count,
                s.bookmark_rate,
                s.total_watch_count,
                s.completed_watch_count,
                s.completion_rate
            FROM tag_home_stats s
            JOIN tags t ON t.tag_id = s.tag_id
            WHERE s.stat_date = ?
              AND t.priority = 1
              AND t.is_active = 1
            ORDER BY s.total_view_count DESC, s.tag_id ASC
            """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new AdminHomeTagStatsResponse(
                        rs.getDate("stat_date").toLocalDate(),
                        rs.getLong("tag_id"),
                        rs.getString("tag_name"),
                        rs.getLong("total_view_count"),
                        rs.getLong("total_bookmark_count"),
                        rs.getBigDecimal("bookmark_rate"),
                        rs.getLong("total_watch_count"),
                        rs.getLong("completed_watch_count"),
                        rs.getBigDecimal("completion_rate")
                ),
                Date.valueOf(statDate)
        );
    }
}
