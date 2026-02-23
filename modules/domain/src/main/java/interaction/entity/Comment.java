package interaction.entity;

import common.entity.BaseTimeEntity;
import common.enums.CommentStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import user.entity.User;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "comments")
public class Comment extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "comment_id")
  private Long id;

  @Column(name = "video_id", nullable = false)
  private Long videoId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "body", columnDefinition = "TEXT", nullable = false)
  private String body;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private CommentStatus status;

  @Builder
  public Comment(Long videoId, User user, String body, CommentStatus status) {
    this.videoId = videoId;
    this.user = user;
    this.body = body;
    this.status = status != null ? status : CommentStatus.ACTIVE;
  }

  public void updateBody(String body) {
    this.body = body;
  }

  public void delete() {
    this.status = CommentStatus.DELETED;
  }
}