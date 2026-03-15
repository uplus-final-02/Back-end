package org.backend.admin.metrics.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;

@Repository
public class AdminTrendingTimelineQueryRepository {

    @PersistenceContext
    private EntityManager em;

    public List<Object[]> findTrendingRows(Timestamp from, Timestamp to) {
        String sql = """
            SELECT
              calculated_at,
              ranking,
              content_id,
              trending_score,
              delta_view_count,
              delta_bookmark_count,
              delta_completed_count
            FROM trending_history
            WHERE calculated_at >= :from AND calculated_at < :to
            ORDER BY calculated_at ASC, ranking ASC
        """;

        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(sql)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();

        return rows;
    }
}