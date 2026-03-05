package content.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@EqualsAndHashCode
@Embeddable
public class ContentMetricSnapshotId implements Serializable {

    @Column(name = "bucket_start_at", nullable = false)
    private LocalDateTime bucketStartAt;

    @Column(name = "content_id", nullable = false)
    private Long contentId;
}
