package org.backend.userapi.search.repository;

import org.backend.userapi.search.entity.EsSyncFailure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EsSyncFailureRepository extends JpaRepository<EsSyncFailure, Long> {

    // 미해결 + 재시도 횟수 제한 이하인 항목만 조회
    @Query("""
        SELECT f FROM EsSyncFailure f
        WHERE f.resolved = false
          AND f.retryCount < :maxRetry
        ORDER BY f.failedAt ASC
        """)
    List<EsSyncFailure> findRetryTargets(@Param("maxRetry") int maxRetry);

    // 동일 contentId의 미해결 항목 존재 여부
    Optional<EsSyncFailure> findByContentIdAndResolvedFalse(Long contentId);
}