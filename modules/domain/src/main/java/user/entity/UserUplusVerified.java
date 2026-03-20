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
public class UserUplusVerified extends BaseTimeEntity {

  @Id
  @Column(name = "user_id", nullable = false)
  private Long userId;

  @MapsId
  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id")
  private User user;
  
  @Column(name = "phone_number", nullable = false, length = 20)
  private String phoneNumber;

  @Column(name = "is_verified", nullable = false)
  private boolean isVerified;

  @Column(name = "verified_at")
  private LocalDateTime verifiedAt;

  @Column(name = "revoked_at")
  private LocalDateTime revokedAt;
  
  public void verify(String phoneNumber, LocalDateTime verifiedAt) {
	    this.phoneNumber = phoneNumber;
	    this.isVerified = true;
	    this.verifiedAt = verifiedAt != null ? verifiedAt : LocalDateTime.now();
	    this.revokedAt = null;
	}

  public static UserUplusVerified createVerified(User user, String phoneNumber, LocalDateTime verifiedAt) {
	    UserUplusVerified v = new UserUplusVerified(); 
	    v.user = user; 
	    v.verify(phoneNumber, verifiedAt);
	    return v;
	}
  
	public void revoke(LocalDateTime revokedAt) {
	    this.isVerified = false;
	    this.revokedAt = revokedAt != null ? revokedAt : LocalDateTime.now();
	}
}