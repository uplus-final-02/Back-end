package content.repository;

import content.entity.UserContent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserContentRepository extends JpaRepository<UserContent, Long> {
}