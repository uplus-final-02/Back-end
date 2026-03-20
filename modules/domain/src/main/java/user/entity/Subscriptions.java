package user.entity;

import common.entity.BaseTimeEntity;
import common.enums.PlanType;
import common.enums.SubscriptionStatus;
import common.enums.UserStatus;
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

  @Enumerated(EnumType.STRING)
  @Column(name = "plan_type", nullable = false, length = 20)
  private PlanType planType;

  @Enumerated(EnumType.STRING)
  @Column(name = "subscription_status", nullable = false, length = 20)
  private SubscriptionStatus subscriptionStatus;

  @Column(name = "started_at", nullable = false)
  private LocalDateTime startedAt;

  @Column(name = "expires_at", nullable = false)
  private LocalDateTime expiresAt;

  @Builder
  public Subscriptions(User user,
          SubscriptionStatus subscriptionStatus,
          PlanType planType,
          LocalDateTime startedAt,
          LocalDateTime expiresAt) {
      this.user = user;
      this.subscriptionStatus = subscriptionStatus != null ? subscriptionStatus : SubscriptionStatus.ACTIVE;
      this.planType = planType != null ? planType : PlanType.SUB_BASIC;
      this.startedAt = startedAt != null ? startedAt : LocalDateTime.now();
      this.expiresAt = expiresAt;
  }

  public boolean isAvailable() {
	  return this.subscriptionStatus == SubscriptionStatus.ACTIVE
	           && LocalDateTime.now().isBefore(this.expiresAt);
  }

  public void restart(LocalDateTime startedAt, LocalDateTime expiresAt) {
	    this.subscriptionStatus = SubscriptionStatus.ACTIVE;
	    this.startedAt = startedAt;
	    this.expiresAt = expiresAt;
	}

  public void expire() {
      this.subscriptionStatus = SubscriptionStatus.EXPIRED;
  }

  public void cancel() {
	    this.subscriptionStatus = SubscriptionStatus.CANCELED;
	}
}