package content.repository;

import content.entity.Video;
import content.entity.WatchHistory;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VideoRepository extends JpaRepository<Video, Long> {
  Optional<Video> findByContentIdAndEpisodeNo(Long contentId, Integer episodeNo);
}
