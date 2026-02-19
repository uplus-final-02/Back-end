package user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import user.entity.UplusUserVerified;

public interface UplusUserVerifiedRepository extends JpaRepository<UplusUserVerified, Long> {
}