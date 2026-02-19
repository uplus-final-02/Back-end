package user.repository;

import common.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import user.entity.User;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByNickname(String nickname);

    Optional<User> findByNickname(String nickname);

    @Query("SELECT u.id FROM User u WHERE u.userRole = :role")
    List<Long> findIdsByUserRole(@Param("role") UserRole role);
}
