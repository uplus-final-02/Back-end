package content.repository;

import content.entity.ContentTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ContentTagRepository extends JpaRepository<ContentTag, Long> {

    @Query("SELECT t.name FROM ContentTag ct JOIN ct.tag t WHERE ct.content.id = :contentId AND t.isActive = true")
    List<String> findTagNamesByContentId(@Param("contentId") Long contentId);

    @Query("SELECT ct.content.id, t.name FROM ContentTag ct JOIN ct.tag t WHERE ct.content.id IN :contentIds AND t.isActive = true")
    List<Object[]> findTagNamesByContentIds(@Param("contentIds") List<Long> contentIds);

    @Query("SELECT ct.content.id, t.id, t.name FROM ContentTag ct JOIN ct.tag t " +
        "WHERE ct.content.id IN :contentIds AND t.isActive = true AND t.priority != 0")
    List<Object[]> findPriorityTagDetailsByContentIds(@Param("contentIds") List<Long> contentIds);
}
