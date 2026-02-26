package user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import user.entity.Subscriptions;
import common.enums.UserStatus;

import java.util.Optional;

public interface SubscriptionsRepository extends JpaRepository<Subscriptions, Long> {
  boolean existsByUserIdAndStatus(Long userId, UserStatus status);

  Optional<Subscriptions> findByUserId(Long userId);
}