package content.entity;

import common.entity.BaseTimeEntity;
import common.enums.ContentAccessLevel;
import common.enums.ContentStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "user_contents",
        indexes = {
                @Index(name = "idx_user_contents_parent_content_id", columnList = "parent_content_id"),
                @Index(name = "idx_user_contents_uploader_id_created_at", columnList = "uploader_id, created_at")
        }
)
public class UserContent extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "content_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_content_id", nullable = false)
    private Content parentContent;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", columnDefinition = "json")
    private String description;

    @Column(name = "thumbnail_url", nullable = false, length = 500)
    private String thumbnailUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_status", nullable = false, length = 20)
    private ContentStatus contentStatus;

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
    public UserContent(Content parentContent,
                       String title,
                       String description,
                       String thumbnailUrl,
                       ContentStatus contentStatus,
                       Long uploaderId,
                       ContentAccessLevel accessLevel) {
        this.parentContent = parentContent;
        this.title = (title != null) ? title : "UNTITLED";
        this.description = description;
        this.thumbnailUrl = (thumbnailUrl != null) ? thumbnailUrl : "TEMP_THUMBNAIL_URL";
        this.contentStatus = (contentStatus != null) ? contentStatus : ContentStatus.HIDDEN;

        this.uploaderId = uploaderId;
        this.accessLevel = (accessLevel != null) ? accessLevel : ContentAccessLevel.FREE;

        this.totalViewCount = 0L;
        this.bookmarkCount = 0L;
    }

    public void updateContentStatus(ContentStatus status) {
        if (status != null) this.contentStatus = status;
    }
}