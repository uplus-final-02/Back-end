package org.backend.admin.stats.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
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
     * 홈 노출 태그(priority=1) 기준 집계 데이터 조회
     */
	private static final String AGGREGATE_SQL = """
		    SELECT
		        t.tag_id,
		        ? AS stat_date,
		        COALESCE(SUM(c.total_view_count), 0) AS total_view_count,
		        COALESCE(SUM(c.bookmark_count), 0) AS total_bookmark_count,
		        CASE
		            WHEN COALESCE(SUM(c.total_view_count), 0) = 0 THEN NULL
		            ELSE ROUND(SUM(c.bookmark_count) / SUM(c.total_view_count), 4)
		        END AS bookmark_rate
		    FROM tags t
		    JOIN content_tags ct ON ct.tag_id = t.tag_id
		    JOIN contents c
		      ON c.content_id = ct.content_id
		     AND c.status = 'ACTIVE'
		    WHERE t.priority = 1
		      AND t.is_active = 1
		    GROUP BY t.tag_id
		    """;
    
    /**
     * 통계 UPSERT
     */
    private static final String UPSERT_SQL = """
        INSERT INTO tag_home_stats
        (stat_date, tag_id, total_view_count, total_bookmark_count, bookmark_rate)
        VALUES (?, ?, ?, ?, ?)
        ON DUPLICATE KEY UPDATE
            total_view_count = VALUES(total_view_count),
            total_bookmark_count = VALUES(total_bookmark_count),
            bookmark_rate = VALUES(bookmark_rate),
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
                        rs.getBigDecimal("bookmark_rate")
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
                        ps.setBigDecimal(5, row.bookmarkRate());
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
            BigDecimal bookmarkRate
    ) {
    }
}
