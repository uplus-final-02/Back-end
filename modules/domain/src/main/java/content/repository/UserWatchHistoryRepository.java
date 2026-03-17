package content.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import content.entity.UserWatchHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import content.entity.UserWatchHistory;

public interface UserWatchHistoryRepository extends JpaRepository<UserWatchHistory, Long> {

    @Query("SELECT wh FROM UserWatchHistory wh JOIN FETCH wh.userContent uc WHERE wh.userId = :userId")
    List<UserWatchHistory> findByUserIdWithContent(@Param("userId") Long userId);

    @Query("SELECT uwh FROM UserWatchHistory uwh " +
            "JOIN FETCH uwh.userContent uc " +
            "JOIN FETCH uc.parentContent pc " +
            "WHERE uwh.userId = :userId " +
            "AND uwh.lastWatchedAt >= :since " +
            "ORDER BY uwh.lastWatchedAt DESC")
    List<UserWatchHistory> findUserWatchHistoriesByUserIdAndSince(@Param("userId") Long userId, @Param("since") LocalDateTime since);

    // 1. 특정 유저와 특정 유저 콘텐츠의 시청 이력 단건 조회
    Optional<UserWatchHistory> findByUserIdAndUserContent_Id(Long userId, Long userContentId);

    // 2. 최근 시청 이력 조회 (N+1 방지를 위해 UserContent를 JOIN FETCH)
    @Query("SELECT uwh FROM UserWatchHistory uwh " +
            "JOIN FETCH uwh.userContent uc " +
            "WHERE uwh.userId = :userId " +
            "AND uwh.lastWatchedAt >= :since " +
            "ORDER BY uwh.lastWatchedAt DESC")
    List<UserWatchHistory> findRecentWatchHistories(
            @Param("userId") Long userId,
            @Param("since") LocalDateTime since,
            Pageable pageable
    );

    // 3. 커서 기반 무한 스크롤 조회 (N+1 방지)
    @Query("SELECT uwh FROM UserWatchHistory uwh " +
            "JOIN FETCH uwh.userContent uc " +
            "WHERE uwh.userId = :userId " +
            "AND (:cursor IS NULL OR uwh.id < :cursor) " +
            "ORDER BY uwh.id DESC")
    Slice<UserWatchHistory> findHistoriesByCursor(
            @Param("userId") Long userId,
            @Param("cursor") Long cursor,
            Pageable pageable
    );

    // 4. 이력 삭제/검증용: 이력 ID와 유저 ID로 단건 조회
    Optional<UserWatchHistory> findByIdAndUserId(Long id, Long userId);

    // 5. 추천 시스템 필터링용: 최근 시청한 콘텐츠 ID 목록 추출 (중복 제거)
    @Query("SELECT DISTINCT uwh.userContent.id FROM UserWatchHistory uwh " +
            "WHERE uwh.userId = :userId AND uwh.lastWatchedAt >= :since")
    List<Long> findRecentWatchedContentIds(
            @Param("userId") Long userId,
            @Param("since") LocalDateTime since
    );

}