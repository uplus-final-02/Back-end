package user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import user.entity.Payment;

import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByUserIdOrderByRequestAtDesc(Long userId);
}