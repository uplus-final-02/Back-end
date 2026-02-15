package content.repository;

import content.entity.Video;
import content.entity.WatchHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VideoRepository extends JpaRepository<Video, Long> {
}
