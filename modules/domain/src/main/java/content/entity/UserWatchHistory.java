package content.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

    public void updateLastWatchedAt(LocalDateTime lastWatchedAt) {
        this.lastWatchedAt = lastWatchedAt;
    }

}
