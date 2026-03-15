package org.backend.admin.metrics.service;

import common.enums.MetricJobStatus;
import lombok.RequiredArgsConstructor;
import org.backend.admin.metrics.dto.AdminMetricsDashboardResponse;
import org.backend.admin.metrics.repository.AdminMetricsDashboardQueryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminMetricsDashboardService {

    private final AdminMetricsDashboardQueryRepository repo;

    public AdminMetricsDashboardResponse getDashboard(LocalDateTime base) {
        LocalDateTime to = (base != null) ? base : LocalDateTime.now();
        LocalDateTime from = to.minusHours(24);

        Timestamp tsFrom = Timestamp.valueOf(from);
        Timestamp tsTo = Timestamp.valueOf(to);

        List<Object[]> bucketRows = repo.findSnapshotBuckets24h(tsFrom, tsTo);
        List<AdminMetricsDashboardResponse.BucketSummary> buckets = new ArrayList<>(bucketRows.size());
        for (Object[] r : bucketRows) {
            buckets.add(new AdminMetricsDashboardResponse.BucketSummary(
                    toLocalDateTime(r[0]),
                    toLong(r[1]),
                    toLong(r[2]),
                    toLong(r[3]),
                    toLong(r[4]),
                    (r[5] == null) ? null : MetricJobStatus.valueOf(String.valueOf(r[5])),
                    (r[6] == null) ? null : String.valueOf(r[6])
            ));
        }

        List<Object[]> hourRows = repo.findTrendingHours24h(tsFrom, tsTo);
        List<AdminMetricsDashboardResponse.HourSummary> hours = new ArrayList<>(hourRows.size());
        for (Object[] r : hourRows) {
            hours.add(new AdminMetricsDashboardResponse.HourSummary(
                    toLocalDateTime(r[0]),
                    (r[1] == null) ? null : MetricJobStatus.valueOf(String.valueOf(r[1])),
                    (r[2] == null) ? null : String.valueOf(r[2]),
                    toLong(r[3]),
                    toLong(r[4]),
                    toLong(r[5]),
                    toLong(r[6]),
                    toLong(r[7]),
                    toLong(r[8]),
                    toLong(r[9]),
                    toLong(r[10]) == 1L
            ));
        }

        return new AdminMetricsDashboardResponse(from, to, buckets, hours);
    }

    private static LocalDateTime toLocalDateTime(Object v) {
        if (v == null) return null;
        if (v instanceof Timestamp ts) return ts.toLocalDateTime();
        if (v instanceof LocalDateTime ldt) return ldt;
        return LocalDateTime.parse(String.valueOf(v));
    }

    private static long toLong(Object v) {
        if (v == null) return 0L;
        if (v instanceof Number n) return n.longValue();
        return Long.parseLong(String.valueOf(v));
    }
}