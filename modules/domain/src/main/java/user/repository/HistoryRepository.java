package user.repository;

import java.util.Optional;
import user.entity.History; // <- 여기 임포트 경로 주의! (content.entity가 아님)
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HistoryRepository extends JpaRepository<History, Long> {

  @Query("SELECT h FROM History h JOIN FETCH h.video v JOIN FETCH h.content c WHERE h.user.id = :userId ORDER BY h.lastWatchedAt DESC")
  Slice<History> findSliceByUserIdOrderByUpdatedAtDesc(@Param("userId") Long userId, Pageable pageable);

  Optional<History> findByIdAndUserId(Long id, Long userId);
}