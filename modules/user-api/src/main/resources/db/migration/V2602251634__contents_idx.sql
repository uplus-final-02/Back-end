-- V2602251634__contents_idx.sql
-- contents 테이블의 updated_at 에 인덱스 추가

ALTER TABLE `contents`
  ADD INDEX `idx_contents_updated_at` (`updated_at`);