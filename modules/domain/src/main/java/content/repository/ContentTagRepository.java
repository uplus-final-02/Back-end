package content.repository;

import content.entity.ContentTag;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentTagRepository extends JpaRepository<ContentTag, Long> {
}
