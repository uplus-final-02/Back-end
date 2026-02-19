package content.repository;

import content.entity.Video;
import content.entity.WatchHistory;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VideoRepository extends JpaRepository<Video, Long> {
  @Query("""
        select v
        from content.entity.Video v
        left join fetch v.videoFile
        where v.content.id = :contentId
        order by v.episodeNo asc
    """)
  List<Video> findEpisodesWithVideoFileByContentId(@Param("contentId") Long contentId);

  Optional<Video> findByContentIdAndEpisodeNo(Long contentId, Integer episodeNo);
}
