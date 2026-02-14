package user.entity;

import common.entity.Tag;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Table(name = "user_preferred_tags",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "UQ_user_preferred_tags_user_tag",
            columnNames = {"user_id", "tag_id"}
        )
    },
    indexes = {
        @Index(name = "idx_user_preferred_tags_user_id", columnList = "user_id"),
        @Index(name = "idx_user_preferred_tags_tag_id", columnList = "tag_id")
    }
)
public class UserPreferredTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_preferred_tags_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tag_id", nullable = false)
    private Tag tag;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public UserPreferredTag(User user, Tag tag) {
        this.user = user;
        this.tag = tag;
    }
}
