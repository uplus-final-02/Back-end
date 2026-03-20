package org.backend.admin.metrics.dto;

import common.enums.MetricJobStatus;

import java.time.LocalDateTime;

public record AdminSnapshotBucketSummaryResponse(
        LocalDateTime bucketStartAt,
        long rowsCount,
        long sumDeltaView,
        long sumDeltaBookmark,
        long sumDeltaCompleted,
        MetricJobStatus jobStatus,
        String message
) {}