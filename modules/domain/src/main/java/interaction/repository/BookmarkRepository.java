package interaction.repository;

import content.entity.Content;
import interaction.entity.Bookmark;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

    boolean existsByUserIdAndContentId(Long userId, Long contentId);

    void deleteByUserIdAndContentId(Long userId, Long contentId);

    @Query("SELECT b FROM Bookmark b WHERE b.userId = :userId " +
           "AND (:cursorId IS NULL OR b.id < :cursorId) " +
           "ORDER BY b.id DESC")
    List<Bookmark> findByUserIdWithCursor(@Param("userId") Long userId,
                                          @Param("cursorId") Long cursorId,
                                          Pageable pageable);

    int countByUserId(Long userId);

    @Query("SELECT b FROM Bookmark b " +
        "WHERE b.userId = :userId " +
        "ORDER BY b.createdAt DESC")
    List<Bookmark> findRecentBookmarks(@Param("userId") Long userId, Pageable pageable);

    // [수정] 메서드명을 실제 정렬 방향(ASC)에 맞게 변경: Desc → Asc
    @Query("SELECT b FROM Bookmark b WHERE b.userId = :userId ORDER BY b.createdAt ASC")
    List<Bookmark> findPlaylistByUserIdAsc(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT c FROM Bookmark b, Content c " +
           "WHERE b.userId = :userId AND b.contentId = c.id " +
           "AND c.status = 'ACTIVE' " +
           "ORDER BY b.createdAt DESC")
    List<Content> findRecentBookmarkedContents(@Param("userId") Long userId, Pageable pageable);
}