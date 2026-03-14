package org.backend.userapi.metrics;

import common.enums.MetricJobType;
import content.entity.MetricJobRun;
import content.repository.MetricJobRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MetricJobRunWriterService {

    private final MetricJobRunRepository metricJobRunRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MetricJobRun startSnapshot(LocalDateTime bucketStartAt) {
        return metricJobRunRepository.findByJobTypeAndBucketStartAt(MetricJobType.SNAPSHOT_10M, bucketStartAt)
                .orElseGet(() -> metricJobRunRepository.save(MetricJobRun.startSnapshot(bucketStartAt)));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void success(Long jobRunId, long processedCount, String message) {
        MetricJobRun run = metricJobRunRepository.findById(jobRunId)
                .orElseThrow(() -> new IllegalStateException("JOB_RUN_NOT_FOUND: " + jobRunId));
        run.markSuccess(processedCount, message);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void empty(Long jobRunId, String message) {
        MetricJobRun run = metricJobRunRepository.findById(jobRunId)
                .orElseThrow(() -> new IllegalStateException("JOB_RUN_NOT_FOUND: " + jobRunId));
        run.markEmpty(message);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failed(Long jobRunId, Exception e) {
        MetricJobRun run = metricJobRunRepository.findById(jobRunId)
                .orElseThrow(() -> new IllegalStateException("JOB_RUN_NOT_FOUND: " + jobRunId));
        run.markFailed(safeMessage(e), stackTrace(e));
    }

    private String safeMessage(Exception e) {
        String m = e.getMessage();
        return (m == null || m.isBlank()) ? e.getClass().getSimpleName() : m;
    }

    private String stackTrace(Exception e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}