package user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import user.entity.Subscriptions;
import common.enums.SubscriptionStatus;

public interface SubscriptionsRepository extends JpaRepository<Subscriptions, Long> {
	boolean existsByUser_IdAndSubscriptionStatus(Long userId, SubscriptionStatus subscriptionStatus);
  
	Optional<Subscriptions> findByUser_Id(Long userId);
}