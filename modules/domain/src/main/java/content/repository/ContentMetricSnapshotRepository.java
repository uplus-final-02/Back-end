package content.repository;

import content.entity.ContentMetricSnapshot;
import content.entity.ContentMetricSnapshotId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ContentMetricSnapshotRepository extends JpaRepository<ContentMetricSnapshot, ContentMetricSnapshotId> {
    /**
     * 복합키 내부의 bucketStartAt 필드와 contentId 목록으로 과거 스냅샷 일괄 조회
     * N+1 문제 방지를 위해 IN 절을 사용하여 한 번의 쿼리로 처리
     * @param bucketStartAt 직전 버킷 기준 시각 (예: 10분 전)
     * @param contentIds 현재 처리 중인 청크 내의 콘텐츠 ID 리스트
     * @return 조회된 직전 스냅샷 목록
     */
    List<ContentMetricSnapshot> findByIdBucketStartAtAndContentIdIn(LocalDateTime bucketStartAt, List<Long> contentIds);


    /**
     * 특정 시간 범위 내의 스냅샷 데이터를 조회.
     * 인기 차트 산출 시 최근 1시간(6개 버킷)의 데이터를 가져오기 위해 사용.
     * N+1 문제 방지를 위해 연관된 Content 엔티티를 패치 조인(JOIN FETCH)으로 함께 조회.
     * @param startTime 조회 시작 시간 (포함)
     * @param endTime 조회 종료 시간 (미포함)
     * @return 지정된 시간 범위의 스냅샷 목록
     */
    @Query("""
        SELECT s
        FROM ContentMetricSnapshot s JOIN FETCH s.content
        WHERE s.id.bucketStartAt >= :startTime AND s.id.bucketStartAt < :endTime
    """)
    List<ContentMetricSnapshot> findSnapshotsByTimeRangeWithContent(
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );
}
