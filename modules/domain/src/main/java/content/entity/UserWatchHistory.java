package content.entity;

import common.entity.BaseTimeEntity;
import common.enums.HistoryStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "user_watch_histories",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_watch_history_user_content", // 제약조건 이름 명시 (선택사항이나 권장)
                columnNames = {"user_id", "content_id"}
        ),
        indexes = {
                // DDL에 있는 인덱스 명시 (선택사항이지만 권장)
                @Index(name = "idx_watch_histories_user", columnList = "user_id"),
                @Index(name = "idx_watch_histories_content", columnList = "content_id"),
                @Index(name = "idx_watch_histories_last_watched", columnList = "last_watched_at")
        }
)

@SQLDelete(sql = "UPDATE user_watch_histories SET deleted_at = NOW() WHERE history_id = ?")
@SQLRestriction("deleted_at IS NULL")
public class UserWatchHistory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "content_id", nullable = false)
    private UserContent userContent;

    @Column(name = "last_watched_at", nullable = false)
    private LocalDateTime lastWatchedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // ★ Builder 패턴 적용 (생성자)
    @Builder
    public UserWatchHistory(Long userId, UserContent userContent, LocalDateTime lastWatchedAt) {
        this.userId = userId;
        this.userContent = userContent;
        this.lastWatchedAt = lastWatchedAt;
    }

}
