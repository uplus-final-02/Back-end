package content.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "trending_history",
    indexes = {
        @Index(name = "idx_trending_history_calculated_at_ranking", columnList = "calculated_at, ranking")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class TrendingHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Long id;

    // 물리적 FK 제약조건 의도적 생략 (배치 쓰기 성능 확보 및 데드락 방지)
    @Column(name = "content_id", nullable = false)
    private Long contentId;

    @Column(name = "ranking", nullable = false)
    private Integer ranking;

    @Column(name = "trending_score", nullable = false)
    private Double trendingScore;

    @Column(name = "delta_view_count", nullable = false)
    private Long deltaViewCount;

    @Column(name = "delta_bookmark_count", nullable = false)
    private Long deltaBookmarkCount;

    @Column(name = "delta_completed_count", nullable = false)
    private Long deltaCompletedCount;

    @Column(name = "calculated_at", nullable = false)
    private LocalDateTime calculatedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public TrendingHistory(Long contentId, Integer ranking, Double trendingScore,
                           Long deltaViewCount, Long deltaBookmarkCount, Long deltaCompletedCount,
                           LocalDateTime calculatedAt) {
        this.contentId = contentId;
        this.ranking = ranking;
        this.trendingScore = trendingScore;
        this.deltaViewCount = deltaViewCount;
        this.deltaBookmarkCount = deltaBookmarkCount;
        this.deltaCompletedCount = deltaCompletedCount;
        this.calculatedAt = calculatedAt;
    }
}