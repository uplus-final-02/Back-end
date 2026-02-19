package user.entity;

import common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "user_uplus_verified")
public class UplusUserVerified extends BaseTimeEntity {

  // user_id가 PK이므로 @Id를 붙입니다.
  @Id
  @Column(name = "user_id")
  private Long userId;

  @Column(name = "phone_number", length = 20)
  private String phoneNumber;

  @Column(name = "is_verified", nullable = false)
  private boolean isVerified;

  @Column(name = "verified_at")
  private LocalDateTime verifiedAt;

  @Column(name = "revoked_at")
  private LocalDateTime revokedAt;

}