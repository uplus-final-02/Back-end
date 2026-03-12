-- Consumer 멱등성: 동일 eventId 중복 처리 방지
-- transcode() 성공 시 VideoFile DONE 업데이트와 동일 트랜잭션으로 INSERT
CREATE TABLE IF NOT EXISTS processed_kafka_events
(
    event_id     VARCHAR(36) NOT NULL COMMENT 'Kafka 이벤트 UUID — 중복 처리 방지 키',
    video_id     BIGINT      NOT NULL COMMENT '처리된 비디오 ID',
    processed_at DATETIME(3) NOT NULL DEFAULT NOW(3),

    PRIMARY KEY (event_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
