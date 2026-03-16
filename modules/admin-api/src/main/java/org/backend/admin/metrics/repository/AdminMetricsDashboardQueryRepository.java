package org.backend.admin.metrics.repository;

import common.enums.MetricJobType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;

@Repository
public class AdminMetricsDashboardQueryRepository {

    @PersistenceContext
    private EntityManager em;

    public List<Object[]> findSnapshotBuckets24h(Timestamp from, Timestamp to) {
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
        """;

        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(sql)
                .setParameter("jobType", MetricJobType.SNAPSHOT_10M.name())
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();

        return rows;
    }

    public List<Object[]> findTrendingHours24h(Timestamp from, Timestamp to) {
        String sql = """
            SELECT
              r.calculated_at                                              AS calculated_at,
              r.status                                                     AS job_status,
              r.message                                                    AS job_message,

              COALESCE(th.cnt, 0)                                          AS trending_rows_count,
              COALESCE(th.sum_dv, 0)                                       AS trending_sum_dv,
              COALESCE(th.sum_db, 0)                                       AS trending_sum_db,
              COALESCE(th.sum_dc, 0)                                       AS trending_sum_dc,

              COALESCE(ss.sum_dv, 0)                                       AS snapshot_sum_dv,
              COALESCE(ss.sum_db, 0)                                       AS snapshot_sum_db,
              COALESCE(ss.sum_dc, 0)                                       AS snapshot_sum_dc,

              CASE
                WHEN COALESCE(th.sum_dv,0) = COALESCE(ss.sum_dv,0)
                 AND COALESCE(th.sum_db,0) = COALESCE(ss.sum_db,0)
                 AND COALESCE(th.sum_dc,0) = COALESCE(ss.sum_dc,0)
                THEN 0 ELSE 1
              END                                                          AS mismatch

            FROM metric_job_runs r

            LEFT JOIN (
              SELECT
                calculated_at,
                COUNT(*)                      AS cnt,
                SUM(delta_view_count)         AS sum_dv,
                SUM(delta_bookmark_count)     AS sum_db,
                SUM(delta_completed_count)    AS sum_dc
              FROM trending_history
              WHERE calculated_at >= :from AND calculated_at < :to
              GROUP BY calculated_at
            ) th
              ON th.calculated_at = r.calculated_at

            LEFT JOIN (
              SELECT
                r2.calculated_at                      AS calculated_at,
                SUM(s.delta_view_count)               AS sum_dv,
                SUM(s.delta_bookmark_count)           AS sum_db,
                SUM(s.delta_completed_user_count)     AS sum_dc
              FROM metric_job_runs r2
              JOIN content_metric_snapshots s
                ON s.bucket_start_at > DATE_SUB(r2.calculated_at, INTERVAL 1 HOUR)
               AND s.bucket_start_at <= r2.calculated_at
              WHERE r2.job_type = :jobType
                AND r2.calculated_at >= :from AND r2.calculated_at < :to
              GROUP BY r2.calculated_at
            ) ss
              ON ss.calculated_at = r.calculated_at

            WHERE r.job_type = :jobType
              AND r.calculated_at >= :from AND r.calculated_at < :to
            ORDER BY r.calculated_at DESC
        """;

        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(sql)
                .setParameter("jobType", MetricJobType.TRENDING_1H.name())
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();

        return rows;
    }
}