-- V__extend_video_transcode_outbox.sql
-- Outbox 통합: ADMIN/USER 구분 + topic을 row에 저장

ALTER TABLE video_transcode_outbox
    ADD COLUMN target_type VARCHAR(16) NOT NULL DEFAULT 'ADMIN' COMMENT 'ADMIN | USER' AFTER event_id,
  ADD COLUMN target_id   BIGINT      NOT NULL DEFAULT 0       COMMENT 'video_file_id or user_video_file_id' AFTER target_type,
  ADD COLUMN topic       VARCHAR(128) NOT NULL DEFAULT 'video.transcode.admin.requested' COMMENT '발행할 Kafka topic' AFTER target_id;

-- 기존 admin 데이터 마이그레이션 (기존 video_file_id 컬럼 유지한 상태에서)
UPDATE video_transcode_outbox
SET
    target_type = 'ADMIN',
    target_id   = video_file_id,
    topic       = 'video.transcode.admin.requested'
WHERE target_id = 0;

-- 폴링 조회 최적화
CREATE INDEX idx_outbox_topic_created_at ON video_transcode_outbox(topic, created_at);