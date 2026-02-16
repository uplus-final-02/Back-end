package content.entity;

import common.entity.BaseTimeEntity;
import common.enums.HistoryStatus;
import content.entity.Video; // 패키지 경로에 맞게 수정 필요
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "watch_histories",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_watch_history_user_video", // 제약조건 이름 명시 (선택사항이나 권장)
        columnNames = {"user_id", "video_id"}
    ),
    indexes = {
        // DDL에 있는 인덱스 명시 (선택사항이지만 권장)
        @Index(name = "idx_watch_histories_user", columnList = "user_id"),
        @Index(name = "idx_watch_histories_content", columnList = "content_id"),
        @Index(name = "idx_watch_histories_video_id", columnList = "video_id"),
        @Index(name = "idx_watch_histories_last_watched", columnList = "last_watched_at"),
        @Index(name = "idx_watch_histories_completed", columnList = "content_id, completed_at")
    }
)
public class WatchHistory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "content_id", nullable = false)
    private Long contentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "video_id", nullable = false)
    private Video video;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private HistoryStatus status;

    @Column(name = "last_position_sec", nullable = false)
    private Integer lastPositionSec;

    @Column(name = "last_watched_at")
    private LocalDateTime lastWatchedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    // ★ Builder 패턴 적용 (생성자)
    @Builder
    public WatchHistory(Long userId, Video video, HistoryStatus status, Integer lastPositionSec, LocalDateTime lastWatchedAt, LocalDateTime completedAt) {
        this.userId = userId;
        this.video = video;
        this.contentId = video.getContent().getId();

        // 값이 들어오면 그 값을 쓰고, 안 들어오면(null) 초기 기본값을 사용
        this.status = status != null ? status : HistoryStatus.STARTED;
        this.lastPositionSec = lastPositionSec != null ? lastPositionSec : 0;
        this.lastWatchedAt = lastWatchedAt != null ? lastWatchedAt : LocalDateTime.now();
        this.completedAt = completedAt;
    }

    // [비즈니스 메서드] 위치 업데이트
    public void updatePosition(Integer position) {
        this.lastPositionSec = position;
        this.lastWatchedAt = LocalDateTime.now();
    }

    // [비즈니스 메서드] 상태 변경
    public void updateStatus(HistoryStatus newStatus) {
        this.status = newStatus;
    }

    // [비즈니스 메서드] 완독 처리
    public void markAsCompleted() {
        this.status = HistoryStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }
}