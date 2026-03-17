package content.repository;

import content.entity.UserContent;


import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;


public interface UserContentRepository extends JpaRepository<UserContent, Long> {

    /**
     * 인덱싱용 커서 기반 ID 페이징 조회 — Step 1.
     *
     * <p>컬렉션 FETCH JOIN 없이 ID만 가져와 Pageable LIMIT이 정확하게 적용되도록 한다.
     * FETCH JOIN + DISTINCT + Pageable 조합은 SQL LIMIT이 먼저 적용된 후 JPA가
     * 메모리에서 중복을 제거하므로, 태그가 많은 부모가 끼면 실제 결과가 PAGE_SIZE보다
     * 작아져 인덱싱 루프가 조기 종료될 수 있다.
     *
     * @param lastId   이전 배치의 마지막 ID (첫 호출 시 0L)
     * @param pageable 페이지 크기 (보통 500)
     */
    @Query("""
            SELECT uc.id FROM UserContent uc
            WHERE uc.contentStatus = 'ACTIVE'
              AND uc.id > :lastId
            ORDER BY uc.id ASC
            """)
    List<Long> findActiveIdsCursor(@Param("lastId") Long lastId, Pageable pageable);

    /**
     * ID 목록으로 UserContent + parentContent + contentTags + tag 일괄 조회 — Step 2.
     *
     * <p>Step 1에서 정확하게 추출한 ID 목록을 IN 절로 조회하므로
     * 컬렉션 조인에 의한 페이징 오염 없이 N+1을 방지할 수 있다.
     *
     * @param ids Step 1에서 얻은 userContent ID 목록
     */
    @Query("""
            SELECT DISTINCT uc FROM UserContent uc
            LEFT JOIN FETCH uc.parentContent pc
            LEFT JOIN FETCH pc.contentTags ct
            LEFT JOIN FETCH ct.tag
            WHERE uc.id IN :ids
            ORDER BY uc.id ASC
            """)
    List<UserContent> findAllWithParentTagsByIds(@Param("ids") List<Long> ids);

    /**
     * DB Fallback 추천용 Step 1: ACTIVE 콘텐츠 인기순 ID 조회.
     *
     * <p>컬렉션 JOIN 없이 ID만 조회하여 Pageable LIMIT이 정확하게 적용된다.
     * Step 2에서 {@link #findAllWithParentTagsByIds}로 FETCH JOIN 조회한다.
     *
     * @param pageable 조회 크기 (보통 50)
     */
    @Query("""
            SELECT uc.id FROM UserContent uc
            WHERE uc.contentStatus = 'ACTIVE'
            ORDER BY uc.totalViewCount DESC, uc.bookmarkCount DESC
            """)
    List<Long> findTopActiveIdsByPopularity(Pageable pageable);
    
 // 크리에이터 탭: 특정 관리자 콘텐츠에 매핑된 ACTIVE 유저 콘텐츠 조회
    @Query("""
        SELECT uc FROM UserContent uc
        JOIN FETCH uc.parentContent pc
        WHERE uc.parentContent.id = :parentContentId
          AND uc.contentStatus = 'ACTIVE'
        ORDER BY uc.createdAt DESC
        """)
    List<UserContent> findActiveByParentContentId(
            @Param("parentContentId") Long parentContentId,
            Pageable pageable);

    // 실시간 동기화용 — updatedAt + id 복합 커서
    @Query("""
        SELECT uc FROM UserContent uc
        WHERE (uc.updatedAt > :watermark)
           OR (uc.updatedAt = :watermark AND uc.id > :lastId)
        ORDER BY uc.updatedAt ASC, uc.id ASC
        """)
    List<UserContent> findUpdatedAfterCursor(
            @Param("watermark") LocalDateTime watermark,
            @Param("lastId") Long lastId,
            Pageable pageable);
    
    @Query("""
            SELECT uc FROM UserContent uc
            JOIN FETCH uc.parentContent pc
            WHERE uc.contentStatus = 'ACTIVE' 
            ORDER BY uc.createdAt DESC
            """)
        List<UserContent> findAllActiveContents(Pageable pageable);
    
 // 특정 유저의 영상 목록 Fallback (N+1 방지)
    @Query("SELECT uc FROM UserContent uc JOIN FETCH uc.parentContent pc WHERE uc.uploaderId = :uploaderId AND uc.contentStatus = 'ACTIVE' ORDER BY uc.createdAt DESC")
    List<UserContent> findActiveByUploaderId(@Param("uploaderId") Long uploaderId, Pageable pageable);

    // 키워드 검색 Fallback (N+1 방지)
    @Query("SELECT uc FROM UserContent uc JOIN FETCH uc.parentContent pc WHERE uc.contentStatus = 'ACTIVE' AND uc.title LIKE %:keyword% ORDER BY uc.createdAt DESC")
    List<UserContent> findActiveByKeyword(@Param("keyword") String keyword, Pageable pageable);
}
