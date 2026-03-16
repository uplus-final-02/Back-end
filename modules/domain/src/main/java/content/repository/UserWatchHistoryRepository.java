package content.repository;

import content.entity.UserWatchHistory;
import content.entity.WatchHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserWatchHistoryRepository extends JpaRepository<UserWatchHistory, Long> {
    Optional<UserWatchHistory> findByUserId(Long userId);

}