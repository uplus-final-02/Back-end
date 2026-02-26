package user.repository;

import common.enums.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import user.entity.Subscriptions;

import java.util.Optional;

public interface SubscriptionsRepository extends JpaRepository<Subscriptions, Long> {
  Optional<Subscriptions> findByUser_Id(Long userId);

  boolean existsByUser_IdAndSubscriptionStatus(Long userId, SubscriptionStatus subscriptionStatus);
}