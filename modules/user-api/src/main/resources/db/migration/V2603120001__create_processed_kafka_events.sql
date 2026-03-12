-- Kafka 이벤트 중복 처리 방지 테이블
-- transcoder-worker의 멱등성 보장 (at-least-once → exactly-once)
-- event_id: Kafka 이벤트 UUID (PK → 중복 INSERT 자동 방지)
CREATE TABLE IF NOT EXISTS processed_kafka_events (
    event_id      VARCHAR(255) NOT NULL,
    video_file_id BIGINT       NOT NULL,
    processed_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (event_id),
    INDEX idx_processed_kafka_events_video_file_id (video_file_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
