package content.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import content.entity.Content;

public interface ContentRepository extends JpaRepository<Content, Long> {

    List<Content> findByUpdatedAtAfter(LocalDateTime updatedAt);
    
    @Query("SELECT DISTINCT c FROM Content c LEFT JOIN FETCH c.tags")
    List<Content> findAllWithTags();
}
