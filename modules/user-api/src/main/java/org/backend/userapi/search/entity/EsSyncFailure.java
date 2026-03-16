package org.backend.userapi.search.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "es_sync_failure", indexes = {
    @Index(name = "idx_es_sync_failure_content_id", columnList = "content_id"),
    @Index(name = "idx_es_sync_failure_retry_count", columnList = "retry_count")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EsSyncFailure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "content_id", nullable = false)
    private Long contentId;

    @Column(name = "failed_at", nullable = false)
    private LocalDateTime failedAt;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "resolved", nullable = false)
    private boolean resolved;

    public static EsSyncFailure of(Long contentId, String errorMessage) {
        EsSyncFailure failure = new EsSyncFailure();
        failure.contentId = contentId;
        failure.failedAt = LocalDateTime.now();
        failure.retryCount = 0;
        failure.lastError = errorMessage != null
                ? errorMessage.substring(0, Math.min(errorMessage.length(), 500))
                : null;
        failure.resolved = false;
        return failure;
    }

    public void incrementRetry(String errorMessage) {
        this.retryCount++;
        this.lastError = errorMessage != null
                ? errorMessage.substring(0, Math.min(errorMessage.length(), 500))
                : null;
        this.failedAt = LocalDateTime.now();
    }

    public void markResolved() {
        this.resolved = true;
    }
}