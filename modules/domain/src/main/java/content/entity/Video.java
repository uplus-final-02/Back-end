package content.entity;

import common.entity.BaseTimeEntity;
import common.enums.VideoStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "videos",
    indexes = {
        @Index(
            name = "idx_video_content_id",
            columnList = "content_id"
        ) // 시리즈에 속한 비디오 목록 조회용 인덱스
    },
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_video_content_episode", // 제약조건 이름
            columnNames = {"content_id", "episode_no"} // 복합 유니크 키
        )
    }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Video extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "video_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "content_id", nullable = false)
    private Content content;

    @Column(name = "episode_no", nullable = false)
    private Integer episodeNo; // 회차 번호 (1화, 2화...)

    @Column(length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Column(name = "view_count", nullable = false)
    private Long viewCount = 0L;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VideoStatus status;

    // 양방향 매핑 (Video <-> VideoFile)
    // Video가 삭제되면 VideoFile도 같이 삭제(Cascade)
    @OneToOne(mappedBy = "video", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private VideoFile videoFile;

    // Builder 패턴 적용 (생성자)
    @Builder
    public Video(Long contentId, Integer episodeNo, String title, String description, String thumbnailUrl, VideoStatus status) {
        this.content = content;
        this.episodeNo = episodeNo;
        this.title = title;
        this.description = description;
        this.thumbnailUrl = thumbnailUrl;
        this.status = (status != null) ? status : VideoStatus.DRAFT;
    }

    // 비즈니스 로직: 조회수 증가
    public void incrementViewCount() {
        this.viewCount++;
    }
}
