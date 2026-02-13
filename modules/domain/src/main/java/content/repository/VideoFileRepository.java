package content.repository;

import content.entity.VideoFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VideoFileRepository extends JpaRepository<VideoFile, Long> {
    Optional<VideoFile> findByVideoId(Long videoId);
}