package content.repository;

import common.enums.ContentStatus;
import content.entity.Content;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ContentRepository extends JpaRepository<Content, Long> {

    List<Content> findByUpdatedAtAfter(LocalDateTime updatedAt);

    List<Content> findByUploaderIdIsNull(Sort sort);

    List<Content> findByUploaderIdIsNotNull(Sort sort);

    @Query("SELECT DISTINCT c FROM Content c LEFT JOIN FETCH c.contentTags ct LEFT JOIN FETCH ct.tag")
    List<Content> findAllWithTags();

    @Query("SELECT DISTINCT c FROM Content c " +
            "LEFT JOIN FETCH c.contentTags ct " +
            "LEFT JOIN FETCH ct.tag t " +
            "WHERE (:status IS NULL OR c.status = :status) " + // status가 null이면 조건 무시(전체조회)
            "AND (:uploaderType IS NULL " +
            "     OR (:uploaderType = 'ADMIN' AND c.uploaderId IS NULL) " +
            "     OR (:uploaderType = 'USER' AND c.uploaderId IS NOT NULL)) " +
            "AND (:tag IS NULL OR :tag = '' " +
            "     OR EXISTS (SELECT 1 FROM c.contentTags sub_ct JOIN sub_ct.tag sub_t " +
            "                WHERE sub_t.name = :tag AND sub_t.isActive = true)) " +
            "ORDER BY c.createdAt DESC")
    List<Content> findContentsWithFilters(
            @Param("status") ContentStatus status,
            @Param("uploaderType") String uploaderType,
            @Param("tag") String tag
    );

    @Query("SELECT DISTINCT c FROM Content c " +
            "WHERE (:status IS NULL OR c.status = :status) " +
            "AND (:uploaderType IS NULL " +
            "     OR (:uploaderType = 'ADMIN' AND c.uploaderId IS NULL) " +
            "     OR (:uploaderType = 'USER' AND c.uploaderId IS NOT NULL)) " +
            "AND (:tag IS NULL OR :tag = '' " +
            "     OR EXISTS (SELECT 1 FROM c.contentTags sub_ct JOIN sub_ct.tag sub_t " +
            "                WHERE sub_t.name = :tag AND sub_t.isActive = true))")
    Slice<Content> findContentsWithFilters(
            @Param("status") ContentStatus status,
            @Param("uploaderType") String uploaderType,
            @Param("tag") String tag,
            Pageable pageable
    );
  
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Content c SET c.totalViewCount = c.totalViewCount + :delta WHERE c.id = :id")
      void incrementViewCount(@Param("id") Long id, @Param("delta") Long delta);

    /**
     * 특정 시간(10분 전 등) 이후에 수정된 콘텐츠만 조회 (타겟팅 최적화)
     * @param lastUpdatedAt 기준 시각
     * @param pageable 페이징 정보 (CHUNK_SIZE)
     * @return 변경된 콘텐츠의 Slice
     */
    Slice<Content> findByUpdatedAtGreaterThanEqual(LocalDateTime lastUpdatedAt, Pageable pageable);
}