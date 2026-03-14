package content.repository;

import content.entity.TrendingHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TrendingHistoryRepository extends JpaRepository<TrendingHistory, Long> {
    /**
     * 가장 최근에 트렌딩 점수가 산출된(계산된) 시각을 조회합니다.
     * 스케줄러 장애로 정각에 데이터가 생성되지 않았더라도, 가장 마지막에 성공한 데이터를 찾기 위함입니다.
     */
    @Query("SELECT MAX(t.calculatedAt) FROM TrendingHistory t")
    Optional<LocalDateTime> findLatestCalculatedAt();

    /**
     * 특정 산출 시각(calculatedAt)에 해당하는 트렌딩 이력을 순위(ranking) 오름차순으로 조회합니다.
     * 복합 인덱스(idx_trending_history_calculated_at_ranking)를 타게 되어 성능이 최적화됩니다.
     */
    List<TrendingHistory> findAllByCalculatedAtOrderByRankingAsc(LocalDateTime calculatedAt);

    long countByCalculatedAt(LocalDateTime calculatedAt);
}

