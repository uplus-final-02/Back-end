package content.repository;

import content.entity.Video;
import content.entity.WatchHistory;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.Tuple;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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
  
  List<Video> findAllByContentIdInOrderByEpisodeNoAsc(List<Long> contentIds);

  @Query("""
      SELECT v.content.id AS contentId, v.videoFile.durationSec AS durationSec
      FROM Video v
      WHERE v.id = :videoId
   """)
  Optional<Tuple> findMetaDataById(@Param("videoId") Long videoId);

  @Modifying(clearAutomatically = true)
  @Query("UPDATE Video v SET v.viewCount = v.viewCount + :delta WHERE v.id = :id")
  void incrementViewCount(@Param("id") Long id, @Param("delta") Long delta);
}
