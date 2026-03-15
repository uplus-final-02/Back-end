package content.repository;

import content.entity.UserContent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserContentRepository extends JpaRepository<UserContent, Long> {
    Optional<UserContent> findByIdAndUploaderId(Long id, Long uploaderId);
}