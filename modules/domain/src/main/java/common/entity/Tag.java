package common.entity;

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
@Table(name = "tags",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_tags_name", columnNames = "name")
    },
    indexes = {
        @Index(name = "idx_tags_type", columnList = "type")
    }
)
public class Tag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tag_id")
    private Long id;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "type", nullable = false, length = 20)
    private String type;

    @Builder
    public Tag(String name, Boolean isActive, String type) {
        this.name = name;
        this.isActive = isActive != null ? isActive : true;
        this.type = type;
    }
}
