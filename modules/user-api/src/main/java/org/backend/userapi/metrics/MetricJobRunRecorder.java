package org.backend.userapi.metrics;

import common.enums.MetricJobStatus;
import common.enums.MetricJobType;
import content.entity.MetricJobRun;
import content.repository.MetricJobRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MetricJobRunRecorder {

    private final MetricJobRunRepository metricJobRunRepository;

    @Transactional
    public MetricJobRun startSnapshot(LocalDateTime bucketStartAt) {
        try {
            return metricJobRunRepository.save(MetricJobRun.startSnapshot(bucketStartAt));
        } catch (DataIntegrityViolationException e) {
            return metricJobRunRepository.findByJobTypeAndBucketStartAt(MetricJobType.SNAPSHOT_10M, bucketStartAt)
                    .orElseThrow(() -> e);
        }
    }

    @Transactional
    public MetricJobRun startTrending(LocalDateTime calculatedAt) {
        try {
            return metricJobRunRepository.save(MetricJobRun.startTrending(calculatedAt));
        } catch (DataIntegrityViolationException e) {
            return metricJobRunRepository.findByJobTypeAndCalculatedAt(MetricJobType.TRENDING_1H, calculatedAt)
                    .orElseThrow(() -> e);
        }
    }

    @Transactional
    public void markSuccess(Long jobRunId, long processedCount, String message) {
        MetricJobRun r = metricJobRunRepository.findById(jobRunId)
                .orElseThrow(() -> new IllegalStateException("JOB_RUN_NOT_FOUND: " + jobRunId));
        r.markSuccess(processedCount, message);
    }

    @Transactional
    public void markEmpty(Long jobRunId, String message) {
        MetricJobRun r = metricJobRunRepository.findById(jobRunId)
                .orElseThrow(() -> new IllegalStateException("JOB_RUN_NOT_FOUND: " + jobRunId));
        r.markEmpty(message);
    }

    @Transactional
    public void markFailed(Long jobRunId, String message, Throwable t) {
        MetricJobRun r = metricJobRunRepository.findById(jobRunId)
                .orElseThrow(() -> new IllegalStateException("JOB_RUN_NOT_FOUND: " + jobRunId));

        String stack = buildStackTrace(t);
        r.markFailed(message, stack);
    }

    private String buildStackTrace(Throwable t) {
        if (t == null) return null;
        StringBuilder sb = new StringBuilder(4000);
        sb.append(t).append("\n");
        for (StackTraceElement el : t.getStackTrace()) {
            sb.append("  at ").append(el).append("\n");
            if (sb.length() > 20000) break;
        }
        return sb.toString();
    }
}