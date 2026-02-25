package user.entity;

import common.entity.BaseTimeEntity;
import common.enums.HistoryStatus;
import content.entity.Content;
import content.entity.Video;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "watch_histories")
@SQLDelete(sql = "UPDATE watch_histories SET deleted_at = NOW() WHERE history_id = ?")
@SQLRestriction("deleted_at IS NULL")
public class History extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "history_id")
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "content_id", nullable = false)
  private Content content;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "video_id", nullable = false)
  private Video video;

  @Column(name = "last_position_sec", nullable = false)
  private Integer lastPositionSec;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private HistoryStatus status;

  @Column(name = "last_watched_at", nullable = false)
  private LocalDateTime lastWatchedAt;

  @Column(name = "completed_at")
  private LocalDateTime completedAt; // 시청 완료 전까지 null

  @Column(name = "deleted_at")
  private LocalDateTime deletedAt; // 삭제 전까지 null

  @Builder
  public History(User user, Content content, Video video, Integer lastPositionSec, HistoryStatus status) {
    this.user = user;
    this.content = content;
    this.video = video;
    this.lastPositionSec = lastPositionSec != null ? lastPositionSec : 0;
    this.status = status != null ? status : HistoryStatus.STARTED;
    this.lastWatchedAt = LocalDateTime.now();
  }

  // 시청 위치 및 상태 업데이트 메서드
  public void updatePlayback(Integer lastPositionSec, HistoryStatus status) {
    this.lastPositionSec = lastPositionSec;
    this.status = status;
    this.lastWatchedAt = LocalDateTime.now();

    if (status == HistoryStatus.COMPLETED) {
      this.completedAt = LocalDateTime.now();
    }
  }
}