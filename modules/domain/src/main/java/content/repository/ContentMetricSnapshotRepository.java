package content.repository;

import content.dto.TrendingStatDto;
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
     * @param /bucketStartAt 직전 버킷 기준 시각 (예: 10분 전)
     * @param contentIds 현재 처리 중인 청크 내의 콘텐츠 ID 리스트
     * @return 조회된 직전 스냅샷 목록
     */
    // List<ContentMetricSnapshot> findByIdBucketStartAtAndContentIdIn(LocalDateTime bucketStartAt, List<Long> contentIds);

    // 각 contentId 에 대해 바로 직전 스냅샷을 가져오기. (10분 고정 X)
    @Query("""
        SELECT s FROM ContentMetricSnapshot s
        WHERE s.id.contentId IN :contentIds
        AND s.id.bucketStartAt = (
            SELECT MAX(s2.id.bucketStartAt)
            FROM ContentMetricSnapshot s2
            WHERE s2.id.contentId = s.id.contentId
        )
     """)
    List<ContentMetricSnapshot> findLatestSnapshotsByContentIds(@Param("contentIds") List<Long> contentIds);

    /**
     * 특정 시간 범위 내의 스냅샷 데이터를 콘텐츠별로 그룹화하여 합산 조회.
     * 트렌딩 차트 산출을 위해 최근 1시간(6개 버킷)의 Delta 지표를 집계함.
     * DB 레벨에서 SUM 연산을 수행하여 네트워크 전송량과 애플리케이션 메모리 사용량을 최적화.
     * * @param startTime 조회 시작 시간 (포함, 예: 현재 시각 - 1시간)
     * @param endTime 조회 종료 시간 (미포함, 예: 현재 시각)
     * @return 콘텐츠별 지표 합산 결과 (TrendingStatDto 인터페이스 프로젝션)
     */
    @Query("""
    SELECT s.id.contentId as contentId,
           COALESCE(SUM(s.deltaViewCount), 0) as totalDeltaView,
           COALESCE(SUM(s.deltaBookmarkCount), 0) as totalDeltaBookmark,
           COALESCE(SUM(s.deltaCompletedUserCount), 0) as totalDeltaCompleted
    FROM ContentMetricSnapshot s
    WHERE s.id.bucketStartAt > :startTime AND s.id.bucketStartAt <= :endTime
    GROUP BY s.id.contentId
""")
    List<TrendingStatDto> findAggregatedStats(@Param("startTime") LocalDateTime startTime,
                                              @Param("endTime") LocalDateTime endTime);
}
