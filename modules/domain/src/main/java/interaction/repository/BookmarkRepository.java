package interaction.repository;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import interaction.entity.Bookmark;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {
    
    // [필수] 서비스에서 사용하는 존재 여부 확인 메서드
    boolean existsByUserIdAndContentId(Long userId, Long contentId);
    
    // 커서 페이징 조회
    @EntityGraph(attributePaths = {"content"})
    @Query("SELECT b FROM Bookmark b WHERE b.user.id = :userId " +
           "AND (:cursorId IS NULL OR b.id < :cursorId) " +
           "ORDER BY b.id DESC")
    List<Bookmark> findByUserIdWithCursor(@Param("userId") Long userId, 
                                         @Param("cursorId") Long cursorId, 
                                         Pageable pageable);

    // 전체 개수 조회
    long countByUserId(Long userId);
}