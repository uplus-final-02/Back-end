package content.entity;

import common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicInsert;

import java.time.LocalDateTime;

@Entity
@Table(name = "content_metric_snapshots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@DynamicInsert
public class ContentMetricSnapshot extends BaseTimeEntity {

    // 복합키 매핑
    @EmbeddedId
    private ContentMetricSnapshotId id;

    // 복합키 내의 content_id와 연관관계(ManyToOne) 매핑 연결
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("contentId") // ContentMetricSnapshotId 클래스 내부의 필드명과 일치해야 함
    @JoinColumn(name = "content_id", nullable = false)
    private Content content;

    // --- 스냅샷(누적값) 필드 ---
    @Column(name = "snapshot_view_count", nullable = false)
    private Long snapshotViewCount = 0L;

    @Column(name = "snapshot_bookmark_count", nullable = false)
    private Long snapshotBookmarkCount = 0L;

    // --- 델타(증가분) 필드 ---
    @Column(name = "delta_view_count", nullable = false)
    private Long deltaViewCount = 0L;

    @Column(name = "delta_bookmark_count", nullable = false)
    private Long deltaBookmarkCount = 0L;

    @Column(name = "delta_completed_user_count", nullable = false)
    private Long deltaCompletedUserCount = 0L;

    // --- 메타 필드 ---
    @Column(name = "aggregated_at", nullable = false, updatable = false)
    private LocalDateTime aggregatedAt;

    @Builder
    public ContentMetricSnapshot(ContentMetricSnapshotId id, Content content,
                                 Long snapshotViewCount, Long snapshotBookmarkCount,
                                 Long deltaViewCount, Long deltaBookmarkCount, Long deltaCompletedUserCount,
                                 LocalDateTime aggregatedAt) {
        this.id = id;
        this.content = content;
        // 빌더로 값이 들어오면 그 값을 쓰고, 안 들어오면 기본값 0L 유지
        this.snapshotViewCount = snapshotViewCount != null ? snapshotViewCount : 0L;
        this.snapshotBookmarkCount = snapshotBookmarkCount != null ? snapshotBookmarkCount : 0L;
        this.deltaViewCount = deltaViewCount != null ? deltaViewCount : 0L;
        this.deltaBookmarkCount = deltaBookmarkCount != null ? deltaBookmarkCount : 0L;
        this.deltaCompletedUserCount = deltaCompletedUserCount != null ? deltaCompletedUserCount : 0L;
        this.aggregatedAt = aggregatedAt != null ? aggregatedAt : LocalDateTime.now();
    }

}
