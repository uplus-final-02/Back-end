package org.backend.admin.metrics.dto;

import common.enums.MetricJobStatus;

import java.time.LocalDateTime;
import java.util.List;

public record AdminMetricsDashboardResponse(
        LocalDateTime from,
        LocalDateTime to,
        List<BucketSummary> buckets,
        List<HourSummary> hours
) {
    public record BucketSummary(
            LocalDateTime bucketStartAt,
            long rowsCount,
            long sumDeltaView,
            long sumDeltaBookmark,
            long sumDeltaCompleted,
            MetricJobStatus jobStatus,
            String jobMessage
    ) {}

    public record HourSummary(
            LocalDateTime calculatedAt,
            MetricJobStatus jobStatus,
            String jobMessage,
            long trendingRowsCount,
            long trendingSumDeltaView,
            long trendingSumDeltaBookmark,
            long trendingSumDeltaCompleted,
            long snapshotSumDeltaView,
            long snapshotSumDeltaBookmark,
            long snapshotSumDeltaCompleted,
            boolean mismatch
    ) {}
}