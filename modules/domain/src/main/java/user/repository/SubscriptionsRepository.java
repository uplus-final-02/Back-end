package user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import user.entity.Subscriptions;
import common.enums.UserStatus;

public interface SubscriptionsRepository extends JpaRepository<Subscriptions, Long> {
  boolean existsByUserIdAndStatus(Long userId, UserStatus status);
}