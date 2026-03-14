CREATE TABLE `metric_job_runs` (
                                   `job_run_id` BIGINT NOT NULL AUTO_INCREMENT,
                                   `job_type` VARCHAR(30) NOT NULL COMMENT 'SNAPSHOT_10M / TRENDING_1H',
                                   `bucket_start_at` DATETIME NULL COMMENT '10분 버킷 시작 시각(스냅샷용)',
                                   `calculated_at` DATETIME NULL COMMENT '정각 기준 시각(트렌딩용)',
                                   `status` VARCHAR(20) NOT NULL COMMENT 'STARTED / SUCCESS / EMPTY / FAILED',
                                   `started_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                   `finished_at` DATETIME NULL,
                                   `processed_count` BIGINT NOT NULL DEFAULT 0 COMMENT '처리/저장 건수',
                                   `message` VARCHAR(1000) NULL COMMENT '성공/빈데이터/실패 사유 요약',
                                   `error_stack` TEXT NULL COMMENT '에러 스택(선택)',
                                   `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                   `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

                                   PRIMARY KEY (`job_run_id`),

                                   INDEX `idx_mjr_job_type_time` (`job_type`, `started_at`),
                                   INDEX `idx_mjr_snapshot_bucket` (`job_type`, `bucket_start_at`),
                                   INDEX `idx_mjr_trending_calc` (`job_type`, `calculated_at`),

                                   UNIQUE KEY `uk_mjr_snapshot_bucket` (`job_type`, `bucket_start_at`),
                                   UNIQUE KEY `uk_mjr_trending_calc` (`job_type`, `calculated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;