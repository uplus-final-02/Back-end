package content.repository;

import common.enums.MetricJobType;
import content.entity.MetricJobRun;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface MetricJobRunRepository extends JpaRepository<MetricJobRun, Long> {

    Optional<MetricJobRun> findTopByJobTypeOrderByStartedAtDesc(MetricJobType jobType);

    Page<MetricJobRun> findAllByJobTypeAndStartedAtBetween(
            MetricJobType jobType,
            LocalDateTime from,
            LocalDateTime to,
            Pageable pageable
    );

    Optional<MetricJobRun> findByJobTypeAndBucketStartAt(MetricJobType jobType, LocalDateTime bucketStartAt);

    Optional<MetricJobRun> findByJobTypeAndCalculatedAt(MetricJobType jobType, LocalDateTime calculatedAt);
}