CREATE TABLE `user_watch_histories` (
  `history_id` bigint NOT NULL AUTO_INCREMENT COMMENT '시청이력ID',
  `user_id` bigint NOT NULL COMMENT '사용자ID',
  `content_id` bigint NOT NULL COMMENT '작품 ID',
  `last_watched_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '최초 생성 시각',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '갱신 시각(시청 시마다 업데이트)',
  `deleted_at` datetime DEFAULT NULL COMMENT '삭제일자 (Soft Delete)',
  PRIMARY KEY (`history_id`),
  UNIQUE KEY `UQ_watch_histories_user_content` (`user_id`,`content_id`),
  KEY `idx_watch_histories_user` (`user_id`),
  KEY `idx_watch_histories_content` (`content_id`),
  KEY `idx_watch_histories_last_watched` (`last_watched_at`),
  CONSTRAINT `FK_watch_histories_user_contents` FOREIGN KEY (`content_id`) REFERENCES `user_contents` (`content_id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `FK_watch_histories_user_users` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) AUTO_INCREMENT=1
