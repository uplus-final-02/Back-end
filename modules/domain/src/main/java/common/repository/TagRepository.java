package common.repository;

import common.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TagRepository extends JpaRepository<Tag, Long> {

    List<Tag> findAllByIdIn(List<Long> ids);

    // HNSW 벡터 구성용: is_active=true인 태그를 ID 오름차순으로 전체 조회
    // tag_id 순서가 벡터 인덱스 포지션과 1:1 매핑되므로 정렬 필수
    List<Tag> findAllByIsActiveTrueOrderByIdAsc();
}
