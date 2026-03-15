package content.repository;

import content.entity.UserVideoFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserVideoFileRepository extends JpaRepository<UserVideoFile, Long> {
    Optional<UserVideoFile> findByContent_Id(Long userContentId);
}