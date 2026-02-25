package user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import user.entity.Payment;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
	
	Optional<Payment> findTopByUser_IdOrderByRequestAtDesc(Long userId);
}
