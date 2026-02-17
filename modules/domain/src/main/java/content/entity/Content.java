package content.entity;

import common.entity.BaseTimeEntity;
import common.entity.Tag; 
import common.enums.ContentAccessLevel;
import common.enums.ContentStatus;
import common.enums.ContentType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.HashSet;
import java.util.Set;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "contents",
    indexes = { @Index(name = "idx_contents_title", columnList = "title") }
)
public class Content extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "content_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private ContentType type;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", columnDefinition = "json")
    private String description;

    @Column(name = "thumbnail_url", nullable = false, length = 500)
    private String thumbnailUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ContentStatus status;

    @Column(name = "total_view_count", nullable = false)
    private Long totalViewCount;

    @Column(name = "bookmark_count", nullable = false)
    private Long bookmarkCount;

    @Column(name = "uploader_id", nullable = false)
    private Long uploaderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_level", nullable = false, length = 30)
    private ContentAccessLevel accessLevel;

    // 🚨 핵심: DB의 content_tags 테이블과 매핑
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "content_tags",
        joinColumns = @JoinColumn(name = "content_id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private Set<Tag> tags = new HashSet<>();

    @Builder
    public Content(ContentType type, String title, String description, String thumbnailUrl, ContentStatus status, Long uploaderId, ContentAccessLevel accessLevel, Set<Tag> tags) {
        this.type = type;
        this.title = title;
        this.description = description;
        this.thumbnailUrl = thumbnailUrl;
        this.uploaderId = uploaderId;
        this.status = status != null ? status : ContentStatus.ACTIVE;
        this.accessLevel = accessLevel != null ? accessLevel : ContentAccessLevel.FREE;
        this.tags = tags != null ? tags : new HashSet<>();
        this.totalViewCount = 0L;
        this.bookmarkCount = 0L;
    }

    public void incrementTotalViewCount() { this.totalViewCount++; }
    public void updateBookmarkCount(long count) { this.bookmarkCount = count; }
    
    public Set<Tag> getTags() { return tags; }
}