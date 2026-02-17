package content.entity;

import common.entity.BaseTimeEntity;
import common.enums.ContentAccessLevel;
import common.enums.ContentStatus;
import common.enums.ContentType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "contents",
    indexes = {
        @Index(name = "idx_contents_title", columnList = "title")
    }
)
public class Content extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "content_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private ContentType type; // SINGLE, SERIES

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    // JPA에서 JSON 컬럼은 기본적으로 String으로 매핑하고 DB의 기능을 이용함
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

    // MSA나 느슨한 결합을 위해 객체(User) 대신 ID만 저장
    // NULL 허용 -> NULL이면 관리자(시스템) 업로드
    @Column(name = "uploader_id")
    private Long uploaderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_level", nullable = false, length = 30)
    private ContentAccessLevel accessLevel;

    @OneToMany(mappedBy = "content", fetch = FetchType.LAZY)
    private List<ContentTag> contentTags = new ArrayList<>();

    @Builder
    public Content(ContentType type, String title, String description, String thumbnailUrl, ContentStatus status, Long uploaderId, ContentAccessLevel accessLevel) {
        this.type = type;
        this.title = title;
        this.description = description;
        this.thumbnailUrl = thumbnailUrl;
        this.uploaderId = uploaderId;

        // Default Values 처리
        this.status = status != null ? status : ContentStatus.ACTIVE;
        this.accessLevel = accessLevel != null ? accessLevel : ContentAccessLevel.FREE;
        this.totalViewCount = 0L;
        this.bookmarkCount = 0L;
    }

    // [비즈니스 메서드] 조회수 증가
    public void incrementTotalViewCount() {
        this.totalViewCount++;
    }

    // [비즈니스 메서드] 북마크 수 변경
    public void updateBookmarkCount(long count) {
        this.bookmarkCount = count;
    }
}