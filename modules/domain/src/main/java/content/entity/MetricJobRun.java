package content.entity;

import common.entity.BaseTimeEntity;
import common.enums.MetricJobStatus;
import common.enums.MetricJobType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "metric_job_runs",
        indexes = {
                @Index(name = "idx_mjr_job_type_time", columnList = "job_type, started_at"),
                @Index(name = "idx_mjr_snapshot_bucket", columnList = "job_type, bucket_start_at"),
                @Index(name = "idx_mjr_trending_calc", columnList = "job_type, calculated_at")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_mjr_snapshot_bucket", columnNames = {"job_type", "bucket_start_at"}),
                @UniqueConstraint(name = "uk_mjr_trending_calc", columnNames = {"job_type", "calculated_at"})
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MetricJobRun extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "job_run_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 30)
    private MetricJobType jobType;

    @Column(name = "bucket_start_at")
    private LocalDateTime bucketStartAt;

    @Column(name = "calculated_at")
    private LocalDateTime calculatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MetricJobStatus status;

    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "processed_count", nullable = false)
    private Long processedCount;

    @Column(name = "message", length = 1000)
    private String message;

    @Column(name = "error_stack", columnDefinition = "LONGTEXT")
    private String errorStack;

    public static MetricJobRun startSnapshot(LocalDateTime bucketStartAt) {
        MetricJobRun r = new MetricJobRun();
        r.jobType = MetricJobType.SNAPSHOT_10M;
        r.bucketStartAt = bucketStartAt;
        r.status = MetricJobStatus.STARTED;
        r.startedAt = LocalDateTime.now();
        r.processedCount = 0L;
        return r;
    }

    public static MetricJobRun startTrending(LocalDateTime calculatedAt) {
        MetricJobRun r = new MetricJobRun();
        r.jobType = MetricJobType.TRENDING_1H;
        r.calculatedAt = calculatedAt;
        r.status = MetricJobStatus.STARTED;
        r.startedAt = LocalDateTime.now();
        r.processedCount = 0L;
        return r;
    }

    public void markSuccess(long processedCount, String message) {
        this.status = MetricJobStatus.SUCCESS;
        this.processedCount = processedCount;
        this.message = message;
        this.finishedAt = LocalDateTime.now();
    }

    public void markEmpty(String message) {
        this.status = MetricJobStatus.EMPTY;
        this.processedCount = 0L;
        this.message = message;
        this.finishedAt = LocalDateTime.now();
    }

    public void markFailed(String message, String errorStack) {
        this.status = MetricJobStatus.FAILED;
        this.message = message;
        this.errorStack = errorStack;
        this.finishedAt = LocalDateTime.now();
    }
}