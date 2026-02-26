package user.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "payments",
        indexes = {
                @Index(name = "IDX_payments_user_id", columnList = "user_id"),
                @Index(name = "IDX_payments_subscription_id", columnList = "subscription_id")
        }
)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long id;

    @Column(name = "subscription_id", nullable = false)
    private Long subscriptionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "amount", nullable = false)
    private Integer amount;

    @Column(name = "payment_status", nullable = false, length = 20)
    private String paymentStatus; // SUCCEEDED / FAILED

    @Column(name = "payment_provider", nullable = false, length = 50)
    private String paymentProvider; // TOSS 등

    @Column(name = "request_at", nullable = false)
    private LocalDateTime requestAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;
}