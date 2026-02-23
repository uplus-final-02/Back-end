package interaction.repository;

import common.enums.CommentStatus;
import interaction.entity.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<Comment, Long> {

  // 특정 비디오의 ACTIVE 상태인 댓글만 최신순으로 페이징 조회 (User 정보 같이 Fetch Join 최적화)
  @Query("SELECT c FROM Comment c JOIN FETCH c.user WHERE c.videoId = :videoId AND c.status = :status ORDER BY c.createdAt DESC")
  Page<Comment> findActiveCommentsByVideoId(
      @Param("videoId") Long videoId,
      @Param("status") CommentStatus status,
      Pageable pageable
  );
}