package user.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import user.entity.Payment;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

}
