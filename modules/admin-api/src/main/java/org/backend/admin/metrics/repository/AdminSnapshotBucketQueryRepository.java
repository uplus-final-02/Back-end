package org.backend.admin.metrics.repository;

import common.enums.MetricJobType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;

@Repository
public class AdminSnapshotBucketQueryRepository {

    @PersistenceContext
    private EntityManager em;

    public List<Object[]> findBucketSummariesSnapshot10m(
            Timestamp from,
            Timestamp to,
            int limit,
            int offset
    ) {
        String sql = """
            SELECT
              s.bucket_start_at                                AS bucket_start_at,
              COUNT(*)                                         AS rows_count,
              COALESCE(SUM(s.delta_view_count), 0)             AS sum_delta_view,
              COALESCE(SUM(s.delta_bookmark_count), 0)         AS sum_delta_bookmark,
              COALESCE(SUM(s.delta_completed_user_count), 0)   AS sum_delta_completed,
              r.status                                         AS job_status,
              r.message                                        AS job_message
            FROM content_metric_snapshots s
            LEFT JOIN metric_job_runs r
              ON r.job_type = :jobType
             AND r.bucket_start_at = s.bucket_start_at
            WHERE s.bucket_start_at >= :from
              AND s.bucket_start_at <  :to
            GROUP BY s.bucket_start_at, r.status, r.message
            ORDER BY s.bucket_start_at DESC
            LIMIT :limit OFFSET :offset
        """;

        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(sql)
                .setParameter("jobType", MetricJobType.SNAPSHOT_10M.name())
                .setParameter("from", from)
                .setParameter("to", to)
                .setParameter("limit", limit)
                .setParameter("offset", offset)
                .getResultList();

        return rows;
    }

    public List<Object[]> findSnapshotSumsByContent(Timestamp windowStart, Timestamp windowEnd) {
        String sql = """
            SELECT
              content_id,
              COALESCE(SUM(delta_view_count), 0)            AS sum_dv,
              COALESCE(SUM(delta_bookmark_count), 0)        AS sum_db,
              COALESCE(SUM(delta_completed_user_count), 0)  AS sum_dc
            FROM content_metric_snapshots
            WHERE bucket_start_at > :start AND bucket_start_at <= :end
            GROUP BY content_id
        """;

        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(sql)
                .setParameter("start", windowStart)
                .setParameter("end", windowEnd)
                .getResultList();

        return rows;
    }
}