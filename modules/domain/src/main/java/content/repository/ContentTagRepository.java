package content.repository;

import common.entity.Tag;
import content.entity.ContentTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ContentTagRepository extends JpaRepository<ContentTag, Long> {

    @Query("SELECT t.name FROM ContentTag ct JOIN ct.tag t WHERE ct.content.id = :contentId AND t.isActive = true")
    List<String> findTagNamesByContentId(@Param("contentId") Long contentId);
}
