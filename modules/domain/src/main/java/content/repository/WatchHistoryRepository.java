package content.repository;

import content.entity.WatchHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface WatchHistoryRepository extends JpaRepository<WatchHistory, Long> {
    Optional<WatchHistory> findByUserIdAndVideoId(Long userId, Long videoId);

    // v.content c 까지 한번에 JOIN FETCH
    @Query("SELECT wh FROM WatchHistory wh " +
        "JOIN FETCH wh.video v " +
        "JOIN FETCH v.content c " +
        "WHERE wh.userId = :userId " +
        "AND wh.lastWatchedAt >= :threeMonthsAgo " +
        "ORDER BY wh.lastWatchedAt DESC")
    List<WatchHistory> findRecentWatchHistories(
        @Param("userId") Long userId,
        @Param("threeMonthsAgo") LocalDateTime threeMonthsAgo,
        Pageable pageable
    );
    
    List<WatchHistory> findByUserIdAndVideoIdIn(Long userId, List<Long> videoIds);

    @Query("SELECT wh FROM WatchHistory wh " +
        "JOIN FETCH wh.video v " +
        "LEFT JOIN FETCH v.videoFile vf " +
        "JOIN FETCH v.content c " +
        "WHERE wh.userId = :userId " +
        "AND (:cursor IS NULL OR wh.id < :cursor) " +
        "ORDER BY wh.id DESC")
    Slice<WatchHistory> findHistoriesByCursor(@Param("userId") Long userId, @Param("cursor") Long cursor, Pageable pageable);

    Optional<WatchHistory> findByIdAndUserId(Long id, Long userId);
}