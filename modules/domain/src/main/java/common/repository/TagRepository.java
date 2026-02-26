package common.repository;

import common.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TagRepository extends JpaRepository<Tag, Long> {

    List<Tag> findAllByIdIn(List<Long> ids);

    List<Tag> findByIsActiveTrueAndPriorityIn(List<Long> priorities);

}
