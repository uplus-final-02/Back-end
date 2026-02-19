package content.repository;

import content.entity.Content;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface ContentRepository extends JpaRepository<Content, Long> {

    List<Content> findByUpdatedAtAfter(LocalDateTime updatedAt);

    List<Content> findByUploaderIdIsNull(Sort sort);

    List<Content> findByUploaderIdIsNotNull(Sort sort);
    
    @Query("SELECT DISTINCT c FROM Content c LEFT JOIN FETCH c.contentTags ct LEFT JOIN FETCH ct.tag")
    List<Content> findAllWithTags();
}