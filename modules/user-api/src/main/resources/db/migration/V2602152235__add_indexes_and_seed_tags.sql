-- V2602152235__add_indexes_and_seed_tags.sql
-- 목적:
-- 1) domain entity(@Table indexes) 기준 누락 인덱스 보강
-- 2) 회원가입/콘텐츠 테스트를 위한 기본 태그 시드

-- =========================================================
-- 1. INDEX
-- =========================================================

-- tags
ALTER TABLE `tags`
  ADD INDEX `idx_tags_type` (`type`);

-- contents
ALTER TABLE `contents`
  ADD INDEX `idx_contents_title` (`title`);

-- videos
ALTER TABLE `videos`
  ADD INDEX `idx_video_content_id` (`content_id`);

-- watch_histories
ALTER TABLE `watch_histories`
  ADD INDEX `idx_watch_histories_user` (`user_id`),
  ADD INDEX `idx_watch_histories_content` (`content_id`),
  ADD INDEX `idx_watch_histories_video_id` (`video_id`),
  ADD INDEX `idx_watch_histories_last_watched` (`last_watched_at`),
  ADD INDEX `idx_watch_histories_completed` (`content_id`, `completed_at`);

-- user_preferred_tags
ALTER TABLE `user_preferred_tags`
  ADD INDEX `idx_user_preferred_tags_user_id` (`user_id`),
  ADD INDEX `idx_user_preferred_tags_tag_id` (`tag_id`);


-- =========================================================
-- 2. SEED (tags)
-- =========================================================

INSERT INTO `tags` (`name`, `type`, `is_active`) VALUES
  ('스포츠', 'CATEGORY', TRUE),
  ('게임', 'CATEGORY', TRUE),
  ('뉴스', 'CATEGORY', TRUE),
  ('영화', 'GENRE', TRUE),
  ('음악', 'GENRE', TRUE),
  ('예능', 'GENRE', TRUE)
ON DUPLICATE KEY UPDATE
  `type` = VALUES(`type`),
  `is_active` = VALUES(`is_active`);
