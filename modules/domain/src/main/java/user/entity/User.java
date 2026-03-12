package user.entity;

import common.entity.BaseTimeEntity;
import common.enums.UserRole;
import common.enums.UserStatus;
import common.enums.WithdrawalReason;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "users",
    uniqueConstraints = {
        @UniqueConstraint(name = "UK_users_nickname", columnNames = "nickname")
    }
)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(name = "nickname", nullable = false, length = 30)
    private String nickname;

    @Column(name = "profile_image", length = 255)
    private String profileImage;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_role", nullable = false, length = 20)
    private UserRole userRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_status", nullable = false, length = 20)
    private UserStatus userStatus;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "withdrawal_reason")
    private WithdrawalReason withdrawalReason;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @PrimaryKeyJoinColumn
    private UserUplusVerified uplusVerified;

    @Builder
    public User(String nickname, String profileImage, UserRole userRole, UserStatus userStatus) {
        this.nickname = nickname;
        this.profileImage = profileImage;
        this.userRole = userRole != null ? userRole : UserRole.USER;
        this.userStatus = userStatus != null ? userStatus : UserStatus.ACTIVE;
    }

    public void updateNickname(String newNickname) {
        this.nickname = newNickname;
    }
    
    public void updateProfileImage(String profileImage) {
        this.profileImage = profileImage;
    }
    
    // 탈퇴 로직 메소드
    public void withdraw(WithdrawalReason reason) {
        this.userStatus = UserStatus.WITHDRAW_PENDING;
        this.withdrawalReason = reason;
        this.deletedAt = LocalDateTime.now();
    }
    
    public boolean isWithdrawn() {
        return this.userStatus == UserStatus.DELETED 
            || this.userStatus == UserStatus.WITHDRAW_PENDING;
    }
}
