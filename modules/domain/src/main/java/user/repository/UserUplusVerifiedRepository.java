package user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import user.entity.UserUplusVerified;

public interface UserUplusVerifiedRepository extends JpaRepository<UserUplusVerified, Long> {
	Optional<UserUplusVerified> findByPhoneNumber(String phoneNumber);
    Optional<UserUplusVerified> findByUser_Id(Long userId);
}