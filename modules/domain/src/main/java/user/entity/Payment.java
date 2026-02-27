package user.entity;

import common.entity.BaseTimeEntity;
import common.enums.PaymentMethod;
import common.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "payments",
        indexes = {
                @Index(name = "IDX_payments_user_id", columnList = "user_id"),
                @Index(name = "IDX_payments_subscription_id", columnList = "subscription_id")
        }
)
public class Payment extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "payment_id")
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "subscription_id", nullable = false)
  private Subscriptions subscription;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "amount", nullable = false)
  private Integer amount;

  @Enumerated(EnumType.STRING)
  @Column(name = "payment_status", nullable = false, length = 20)
  private PaymentStatus paymentStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "payment_provider", nullable = false, length = 50)
  private PaymentMethod paymentMethod;

  @Column(name = "request_at", nullable = false)
  private LocalDateTime requestAt;

  @Column(name = "approved_at")
  private LocalDateTime approvedAt;

  @Builder
  public Payment(Subscriptions subscription,
                 User user,
                 Integer amount,
                 PaymentStatus paymentStatus,
                 PaymentMethod paymentMethod,
                 LocalDateTime requestAt,
                 LocalDateTime approvedAt) {

      this.subscription = subscription;
      this.user = user;
      this.amount = amount != null ? amount : 0;

      this.paymentStatus = paymentStatus != null ? paymentStatus : PaymentStatus.FAILED;
      this.paymentMethod = paymentMethod != null ? paymentMethod : PaymentMethod.CARD;

      this.requestAt = requestAt != null ? requestAt : LocalDateTime.now();
      this.approvedAt = approvedAt;
  }

  public void approve(LocalDateTime approvedAt) {
    this.paymentStatus = PaymentStatus.SUCCEEDED;
    this.approvedAt = approvedAt != null ? approvedAt : LocalDateTime.now();
  }

  public void fail() {
    this.paymentStatus = PaymentStatus.FAILED;
    this.approvedAt = null;
  }
}