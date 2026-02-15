package user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import user.entity.UserPreferredTag;

import java.util.List;

public interface UserPreferredTagRepository extends JpaRepository<UserPreferredTag, Long> {

    List<UserPreferredTag> findAllByUserId(Long userId);
}
