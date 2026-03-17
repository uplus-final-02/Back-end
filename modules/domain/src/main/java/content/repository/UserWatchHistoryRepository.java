package content.repository;

import content.entity.UserWatchHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserWatchHistoryRepository extends JpaRepository<UserWatchHistory, Long> {
    Optional<UserWatchHistory> findByUserId(Long userId);

    @Query("SELECT uwh FROM UserWatchHistory uwh " +
            "JOIN FETCH uwh.userContent uc " +
            "JOIN FETCH uc.parentContent pc " +
            "WHERE uwh.userId = :userId " +
            "AND (:cursor IS NULL OR uwh.id < :cursor) " +
            "ORDER BY uwh.id DESC")
    Slice<UserWatchHistory> findHistoriesByCursor(@Param("userId") Long userId, @Param("cursor") Long cursor, Pageable pageable);

    @Query("SELECT wh FROM UserWatchHistory wh JOIN FETCH wh.userContent uc WHERE wh.userId = :userId")
    List<UserWatchHistory> findByUserIdWithContent(@Param("userId") Long userId);

    @Query("SELECT uwh FROM UserWatchHistory uwh " +
            "JOIN FETCH uwh.userContent uc " +
            "JOIN FETCH uc.parentContent pc " +
            "WHERE uwh.userId = :userId " +
            "AND uwh.lastWatchedAt >= :since")
    List<UserWatchHistory> findUserWatchHistoriesByUserIdAndSince(@Param("userId") Long userId, @Param("since") LocalDateTime since);
}