package content.entity;

import common.entity.BaseTimeEntity;
import common.enums.ContentAccessLevel;
import common.enums.ContentStatus;
import common.enums.VideoStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_contents",
        indexes = {
                @Index(name = "idx_user_contents_parent", columnList = "parent_content_id"),
                @Index(name = "idx_user_contents_uploader", columnList = "uploader_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserContent extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_content_id")
    private Long id;

    @Column(name = "parent_content_id", nullable = false)
    private Long parentContentId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "thumbnail_url", nullable = false, length = 500)
    private String thumbnailUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_status", nullable = false, length = 20)
    private ContentStatus contentStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "video_status", nullable = false, length = 20)
    private VideoStatus videoStatus;

    @Column(name = "total_view_count", nullable = false)
    private Long totalViewCount;

    @Column(name = "bookmark_count", nullable = false)
    private Long bookmarkCount;

    @Column(name = "uploader_id", nullable = false)
    private Long uploaderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_level", nullable = false, length = 30)
    private ContentAccessLevel accessLevel;

    @Builder
    public UserContent(Long parentContentId, String title, String thumbnailUrl,
                       ContentStatus contentStatus, VideoStatus videoStatus,
                       Long uploaderId, ContentAccessLevel accessLevel) {
        this.parentContentId = parentContentId;
        this.title = title;
        this.thumbnailUrl = thumbnailUrl;
        this.contentStatus = (contentStatus != null) ? contentStatus : ContentStatus.ACTIVE;
        this.videoStatus = (videoStatus != null) ? videoStatus : VideoStatus.DRAFT;
        this.uploaderId = uploaderId;
        this.accessLevel = (accessLevel != null) ? accessLevel : ContentAccessLevel.FREE;
        this.totalViewCount = 0L;
        this.bookmarkCount = 0L;
    }

    public void markVideoPrivate() {
        this.videoStatus = VideoStatus.PRIVATE;
    }

    public void markVideoPublic() {
        this.videoStatus = VideoStatus.PUBLIC;
    }
}