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
                       PlanType planType,
                       SubscriptionStatus subscriptionStatus,
                       LocalDateTime startedAt,
                       LocalDateTime expiresAt) {
    this.user = user;
    this.planType = planType != null ? planType : PlanType.SUB_BASIC;
    this.subscriptionStatus = subscriptionStatus != null ? subscriptionStatus : SubscriptionStatus.ACTIVE;
    this.startedAt = startedAt != null ? startedAt : LocalDateTime.now();
    this.expiresAt = expiresAt != null ? expiresAt : this.startedAt.plusDays(30);
  }

  public boolean isAvailable() {
    return this.subscriptionStatus == SubscriptionStatus.ACTIVE;
  }
}