package content.repository;

import content.entity.Content;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ContentRepository extends JpaRepository<Content, Long> {

    List<Content> findByUpdatedAtAfter(LocalDateTime updatedAt);

    // 관리자 업로드 (uploaderId IS NULL)
    List<Content> findByUploaderIdIsNull(Sort sort);

    // 일반 유저 업로드 (uploaderId IS NOT NULL)
    List<Content> findByUploaderIdIsNotNull(Sort sort);
}
