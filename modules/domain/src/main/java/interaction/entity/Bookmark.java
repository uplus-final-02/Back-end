package interaction.entity;

import common.entity.BaseTimeEntity;
import content.entity.Content;
import jakarta.persistence.*;
import lombok.*;
import user.entity.User;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "bookmarks",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "UQ_bookmarks_user_content",
            columnNames = {"user_id", "content_id"} // 중복 찜 방지
        )
    }
)
public class Bookmark extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "bookmark_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "content_id", nullable = false)
    private Content content;

    @Builder
    public Bookmark(User user, Content content) {
        this.user = user;
        this.content = content;
    }
}