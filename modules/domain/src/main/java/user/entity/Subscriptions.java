package user.entity;

import common.entity.BaseTimeEntity;
import common.enums.SubscriptionStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "subscriptions")
public class Subscriptions extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "subscription_id")
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;
  
  @Column(name = "plan_type", nullable = false, length = 20)
  private String planType;

  @Enumerated(EnumType.STRING)
  @Column(name = "subscription_status", nullable = false, length = 20)
  private SubscriptionStatus subscriptionStatus;

  @Column(name = "started_at", nullable = false)
  private LocalDateTime startedAt;

  @Column(name = "expires_at", nullable = false)
  private LocalDateTime expiredAt;
  
  @Builder
  public Subscriptions(User user,
          SubscriptionStatus subscriptionStatus,
          String planType,
          LocalDateTime startedAt,
          LocalDateTime expiredAt) {
      this.user = user;
      this.subscriptionStatus = subscriptionStatus != null ? subscriptionStatus : SubscriptionStatus.ACTIVE;
      this.planType = (planType != null && !planType.isBlank()) ? planType : "SUB_BASIC";
      this.startedAt = startedAt != null ? startedAt : LocalDateTime.now();
      this.expiredAt = expiredAt;
  }

  public boolean isAvailable() {
	  return this.subscriptionStatus == SubscriptionStatus.ACTIVE
	           && LocalDateTime.now().isBefore(this.expiredAt);
  }
  
  public void extendTo(LocalDateTime newExpiresAt) {
      this.subscriptionStatus = SubscriptionStatus.ACTIVE;
      this.expiredAt = newExpiresAt;
  }

  public void expire() {
      this.subscriptionStatus = SubscriptionStatus.EXPIRED;
  }
  
}