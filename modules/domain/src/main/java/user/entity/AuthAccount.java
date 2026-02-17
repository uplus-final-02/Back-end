package user.entity;

import common.enums.AuthProvider;
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
@Table(name = "auth_accounts",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "UQ_auth_accounts_provider_subject",
            columnNames = {"auth_provider", "auth_provider_subject"}
        ),
        @UniqueConstraint(
            name = "UQ_auth_accounts_user_provider",
            columnNames = {"user_id", "auth_provider"}
        )
    },
    indexes = {
        @Index(name = "IDX_auth_accounts_user_id", columnList = "user_id")
    }
)
public class AuthAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "auth_account_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false, length = 20)
    private AuthProvider authProvider;

    @Column(name = "auth_provider_subject", nullable = false, length = 255)
    private String authProviderSubject;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Builder
    public AuthAccount(User user, AuthProvider authProvider, String authProviderSubject,
                       String email, String passwordHash) {
        this.user = user;
        this.authProvider = authProvider;
        this.authProviderSubject = authProviderSubject;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    // [비즈니스 메서드] 마지막 로그인 시각 갱신
    public void updateLastLoginAt() {
        this.lastLoginAt = LocalDateTime.now();
    }
}
