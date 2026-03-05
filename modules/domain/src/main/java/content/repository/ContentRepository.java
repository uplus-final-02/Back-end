package content.repository;

import common.enums.ContentStatus;
import common.enums.ContentType;
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

    // ── ES 검색 Fallback: 제목 LIKE + category/genre/tag 필터 (인기순) ────
    @Query("SELECT DISTINCT c FROM Content c " +
           "LEFT JOIN FETCH c.contentTags ct LEFT JOIN FETCH ct.tag " +
           "WHERE c.status = 'ACTIVE' " +
           "AND LOWER(c.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "AND (:category IS NULL OR c.type = :category) " +
           "AND (:genre IS NULL OR EXISTS (" +
           "    SELECT 1 FROM c.contentTags sub_ct1 JOIN sub_ct1.tag sub_t1 " +
           "    WHERE sub_t1.name = :genre AND sub_t1.isActive = true)) " +
           "AND (:tag IS NULL OR EXISTS (" +
           "    SELECT 1 FROM c.contentTags sub_ct2 JOIN sub_ct2.tag sub_t2 " +
           "    WHERE sub_t2.name = :tag AND sub_t2.isActive = true)) " +
           "ORDER BY c.totalViewCount DESC, c.createdAt DESC")
    List<Content> findActiveByTitleLikePopular(
            @Param("keyword") String keyword,
            @Param("category") ContentType category,
            @Param("genre") String genre,
            @Param("tag") String tag,
            Pageable pageable);

    // ── ES 검색 Fallback: 제목 LIKE + category/genre/tag 필터 (최신순) ────
    @Query("SELECT DISTINCT c FROM Content c " +
           "LEFT JOIN FETCH c.contentTags ct LEFT JOIN FETCH ct.tag " +
           "WHERE c.status = 'ACTIVE' " +
           "AND LOWER(c.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "AND (:category IS NULL OR c.type = :category) " +
           "AND (:genre IS NULL OR EXISTS (" +
           "    SELECT 1 FROM c.contentTags sub_ct1 JOIN sub_ct1.tag sub_t1 " +
           "    WHERE sub_t1.name = :genre AND sub_t1.isActive = true)) " +
           "AND (:tag IS NULL OR EXISTS (" +
           "    SELECT 1 FROM c.contentTags sub_ct2 JOIN sub_ct2.tag sub_t2 " +
           "    WHERE sub_t2.name = :tag AND sub_t2.isActive = true)) " +
           "ORDER BY c.createdAt DESC, c.id DESC")
    List<Content> findActiveByTitleLikeLatest(
            @Param("keyword") String keyword,
            @Param("category") ContentType category,
            @Param("genre") String genre,
            @Param("tag") String tag,
            Pageable pageable);

    // ── ES 검색 Fallback: total count (genre/tag 필터 포함) ───────────────
    @Query("SELECT COUNT(DISTINCT c) FROM Content c " +
           "WHERE c.status = 'ACTIVE' " +
           "AND LOWER(c.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "AND (:category IS NULL OR c.type = :category) " +
           "AND (:genre IS NULL OR EXISTS (" +
           "    SELECT 1 FROM c.contentTags sub_ct1 JOIN sub_ct1.tag sub_t1 " +
           "    WHERE sub_t1.name = :genre AND sub_t1.isActive = true)) " +
           "AND (:tag IS NULL OR EXISTS (" +
           "    SELECT 1 FROM c.contentTags sub_ct2 JOIN sub_ct2.tag sub_t2 " +
           "    WHERE sub_t2.name = :tag AND sub_t2.isActive = true))")
    long countActiveByTitleLike(
            @Param("keyword") String keyword,
            @Param("category") ContentType category,
            @Param("genre") String genre,
            @Param("tag") String tag);

    // ── 추천 Fallback: 인기순 (조회수 + 북마크 기준) ──────────────────────
    @Query("SELECT DISTINCT c FROM Content c " +
           "LEFT JOIN FETCH c.contentTags ct LEFT JOIN FETCH ct.tag " +
           "WHERE c.status = 'ACTIVE' " +
           "ORDER BY c.totalViewCount DESC, c.bookmarkCount DESC")
    List<Content> findTopActiveByPopularity(Pageable pageable);

    // ── ES 검색 Fallback (keyword 없음): total count용 ────────────────────
    @Query("SELECT COUNT(c) FROM Content c WHERE c.status = 'ACTIVE'")
    long countAllActive();
}