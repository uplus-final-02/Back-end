-- Outbox 패턴: video 업로드 확정 → Kafka 발행 사이의 원자성 보장
-- 동일 트랜잭션에서 비즈니스 변경 + 이 행 INSERT → 커밋 후 폴링 스케줄러가 Kafka 발행
CREATE TABLE IF NOT EXISTS video_transcode_outbox
(
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    event_id      VARCHAR(36)  NOT NULL COMMENT 'UUID — 이벤트 고유 식별자',
    video_file_id BIGINT       NOT NULL COMMENT '트랜스코딩 대상 파일 ID',
    payload       TEXT         NOT NULL COMMENT 'JSON 직렬화된 VideoTranscodeRequestedEvent',
    created_at    DATETIME(3)  NOT NULL DEFAULT NOW(3),

    PRIMARY KEY (id),
    UNIQUE KEY uq_outbox_event_id (event_id),
    INDEX idx_outbox_created_at (created_at)    -- 폴링 스케줄러 ORDER BY 성능
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
