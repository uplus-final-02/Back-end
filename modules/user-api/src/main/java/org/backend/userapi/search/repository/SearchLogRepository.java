package org.backend.userapi.search.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.backend.userapi.search.entity.SearchLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SearchLogRepository extends JpaRepository<SearchLog, Long> {

    // 결과 없는 검색어 — 사전 후보 추출용
	@Query("""
		SELECT s.keyword, COUNT(s) as cnt
		FROM SearchLog s
		WHERE s.resultCount = 0
		AND s.searchedAt >= :since
		GROUP BY s.keyword
		ORDER BY cnt DESC
		""")
	List<Object[]> findZeroResultKeywords(@Param("since") LocalDateTime since, Pageable pageable);

    // 인기 검색어
    @Query("""
    	SELECT s.keyword, COUNT(s) as cnt
    	FROM SearchLog s
    	WHERE s.searchedAt >= :since
    	GROUP BY s.keyword
    	ORDER BY cnt DESC
    	""")
    List<Object[]> findTopKeywords(@Param("since") LocalDateTime since, Pageable pageable);
    
    @Modifying
    @Query(value = "DELETE FROM search_log WHERE searched_at < :cutoff " +
                   "ORDER BY id ASC LIMIT 10000",
           nativeQuery = true)
    int deleteOldBatch(@Param("cutoff") LocalDateTime cutoff);
}