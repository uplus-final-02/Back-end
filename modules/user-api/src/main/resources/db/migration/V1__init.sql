-- V1__init_schema.sql
-- MySQL 8.x / InnoDB / utf8mb4
-- Tables + PK/FK/NN/default only (indexes/UK are added later except those already defined)

-- NOTE:
-- 1) Flyway는 "DB 자체 생성(CREATE DATABASE)"을 기본으로 책임지기 어렵습니다.
--    이 스크립트는 "sendapp" DB에 연결된 상태에서 실행된다고 가정합니다.
-- 2) billing_history_id는 ERD 의도대로 "논리 FK(polymorphic)" 이므로 물리 FK를 걸지 않습니다.
-- 3) FK 삭제 연쇄를 피하기 위해 기본은 RESTRICT/NO ACTION을 사용합니다.

-- =========================================================
-- 1. REFERENCE
-- =========================================================

-- 통신사 회원 더미 (telecom_members)
CREATE TABLE `telecom_members` (
  `member_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '통신사 회원 식별자',
  `phone_number` VARCHAR(20) NOT NULL COMMENT '통신사 가입 전화번호',
  `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '통신사 이용 상태 (ACTIVE / INACTIVE)',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '회원 등록 시각',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '회원 정보 수정 시각',

  PRIMARY KEY (`member_id`),

  -- 전화번호 중복 방지 + 단일 조회 인덱스 역할
  UNIQUE KEY `UK_telecom_phone` (`phone_number`)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
  
-- 태그 (tags)
CREATE TABLE `tags` (
  `tag_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '태그 식별자',
  `name` VARCHAR(50) NOT NULL COMMENT '태그명',
  `is_active` BOOLEAN NOT NULL DEFAULT TRUE COMMENT '활성여부',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시각',
  `type` VARCHAR(20) NOT NULL COMMENT '태그 유형 (CATEGORY, GENRE)',

  PRIMARY KEY (`tag_id`),

  -- 태그명 중복 방지
  UNIQUE KEY `uq_tags_name` (`name`)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- =========================================================
-- 2. USER / AUTH
-- =========================================================

-- 회원 정보 (users)
CREATE TABLE `users` (
  `user_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '유저 식별자',
  `nickname` VARCHAR(30) NOT NULL COMMENT '중복검사 대상',
  `profile_image` VARCHAR(255) NULL COMMENT '프로필 이미지 경로 (S3/UUID)',
  `user_role` VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT '백오피스 접근 구분 (USER, ADMIN)',
  `user_status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '정지/탈퇴 포함 (ACTIVE, INACTIVE, DELETED)',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일',
  `deleted_at` DATETIME NULL COMMENT '탈퇴일자 (Soft Delete)',

  PRIMARY KEY (`user_id`),
  UNIQUE KEY `UK_users_nickname` (`nickname`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 소셜/인증 계정 (auth_accounts)
CREATE TABLE `auth_accounts` (
  `auth_account_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '인증 계정 식별자',
  `user_id` BIGINT NOT NULL COMMENT '연결된 유저',
  `auth_provider` VARCHAR(20) NOT NULL COMMENT '인증 제공자',
  `auth_provider_subject` VARCHAR(255) NOT NULL COMMENT '제공자 고유 식별자 (sub/id, EMAIL은 email)',
  `email` VARCHAR(255) NULL COMMENT '제공자가 내려준 이메일 (참고용)',
  `password_hash` VARCHAR(255) NULL COMMENT 'EMAIL 로그인 시 해시 저장',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시각',
  `last_login_at` DATETIME NULL COMMENT '최근 로그인 시각',

  PRIMARY KEY (`auth_account_id`),

  -- 중복 방지: 동일 provider에서 동일 subject는 1개만
  UNIQUE KEY `UQ_auth_accounts_provider_subject` (`auth_provider`, `auth_provider_subject`),

  -- 한 유저는 같은 provider를 2개 못 갖게
  UNIQUE KEY `UQ_auth_accounts_user_provider` (`user_id`, `auth_provider`),

  -- FK 성능/제약 안정성
  KEY `IDX_auth_accounts_user_id` (`user_id`),

  CONSTRAINT `FK_auth_accounts_users`
    FOREIGN KEY (`user_id`)
    REFERENCES `users` (`user_id`)
    ON UPDATE RESTRICT
    ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 유플러스 인증 정보 (user_uplus_verified)
CREATE TABLE `user_uplus_verified` (
  `user_id` BIGINT NOT NULL COMMENT '유저 식별자',
  `phone_number` VARCHAR(20) NOT NULL COMMENT '인증된 번호',
  `is_verified` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '인증 여부',
  `verified_at` DATETIME NULL COMMENT '인증 완료 시각',
  `revoked_at` DATETIME NULL COMMENT '인증 해제 시각 (번호 변경/해지 등)',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '최초 인증 시각',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '상태 변경 시각',

  PRIMARY KEY (`user_id`),

  UNIQUE KEY `UK_verified_phone` (`phone_number`),

  CONSTRAINT `FK_user_uplus_verified_users`
    FOREIGN KEY (`user_id`)
    REFERENCES `users` (`user_id`)
    ON UPDATE RESTRICT
    ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- 리프레시 토큰 (tokens_management)
CREATE TABLE `tokens_management` (
  `token_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Refresh Token 식별자',
  `user_id` BIGINT NOT NULL COMMENT '토큰 소유 사용자 ID',
  `token` VARCHAR(500) NOT NULL COMMENT 'JWT Refresh Token 값',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '토큰 발급 시각',
  `expires_at` DATETIME NOT NULL COMMENT '토큰 만료 시각',

  PRIMARY KEY (`token_id`),
  -- 토큰 중복 방지 및 빠른 검증
  UNIQUE KEY `UQ_refresh_tokens_token` (`token`),

  CONSTRAINT `FK_refresh_tokens_users`
    FOREIGN KEY (`user_id`)
    REFERENCES `users` (`user_id`)
    ON UPDATE RESTRICT
    ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 유저 선호 태그 (user_preferred_tags)
CREATE TABLE `user_preferred_tags` (
  `user_preferred_tags_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '매핑 식별자',
  `user_id` BIGINT NOT NULL COMMENT '유저ID',
  `tag_id` BIGINT NOT NULL COMMENT '태그ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록 시각',

  PRIMARY KEY (`user_preferred_tags_id`),

  -- 동일 유저의 동일 태그 중복 설정 방지
  UNIQUE KEY `UQ_user_preferred_tags_user_tag` (`user_id`, `tag_id`),

  CONSTRAINT `FK_user_preferred_tags_users`
    FOREIGN KEY (`user_id`)
    REFERENCES `users` (`user_id`)
    ON UPDATE RESTRICT
    ON DELETE RESTRICT,

  CONSTRAINT `FK_user_preferred_tags_tags`
    FOREIGN KEY (`tag_id`)
    REFERENCES `tags` (`tag_id`)
    ON UPDATE RESTRICT
    ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

  

  
-- =========================================================
-- 3. SUBSCRIPTION / ENTITLEMENT
-- =========================================================

-- 구독 정보 (subscriptions)
CREATE TABLE `subscriptions` (
  `subscription_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '구독 식별자',
  `user_id` BIGINT NOT NULL COMMENT '구독 사용자 ID',
  `plan_type` VARCHAR(20) NOT NULL DEFAULT 'SUB_BASIC' COMMENT '요금제 타입',
  `subscription_status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '구독 상태',
  `started_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '구독 시작 시각',
  `expires_at` DATETIME NOT NULL COMMENT '구독 만료 시각',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시각',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시각',

  PRIMARY KEY (`subscription_id`),

  CONSTRAINT `FK_subscriptions_users`
    FOREIGN KEY (`user_id`)
    REFERENCES `users` (`user_id`)
    ON UPDATE RESTRICT
    ON DELETE RESTRICT

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- 결제 이력 (payments)
CREATE TABLE `payments` (
  `payment_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '결제 식별자',
  `subscription_id` BIGINT NOT NULL COMMENT '연결된 구독 ID',
  `user_id` BIGINT NOT NULL COMMENT '결제 사용자 ID',
  `amount` INT NOT NULL COMMENT '결제 금액',
  `payment_status` VARCHAR(20) NOT NULL COMMENT '결제 처리 결과',
  `payment_provider` VARCHAR(50) NOT NULL NOT NULL COMMENT '결제 제공자',
  `request_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '결제 요청 시각',
  `approved_at` DATETIME NULL COMMENT '결제 승인 시각',

  PRIMARY KEY (`payment_id`),

  CONSTRAINT `FK_payments_subscriptions`
    FOREIGN KEY (`subscription_id`)
    REFERENCES `subscriptions` (`subscription_id`)
    ON UPDATE RESTRICT
    ON DELETE RESTRICT,

  CONSTRAINT `FK_payments_users`
    FOREIGN KEY (`user_id`)
    REFERENCES `users` (`user_id`)
    ON UPDATE RESTRICT
    ON DELETE RESTRICT

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- =========================================================
-- 4. CONTENT
-- =========================================================

-- 콘텐츠 메타데이터 (contents)
CREATE TABLE `contents` (
  `content_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '콘텐츠 ID',
  `type` VARCHAR(20) NOT NULL COMMENT '타입 (SINGLE, SERIES)',
  `title` VARCHAR(200) NOT NULL COMMENT '작품 제목',
  `description` JSON NULL COMMENT '작품 설명 데이터',
  `thumbnail_url` VARCHAR(500) NOT NULL COMMENT '썸네일 URL',
  `status` VARCHAR(20) NOT NULL COMMENT '노출 상태',
  `total_view_count` BIGINT NOT NULL DEFAULT 0 COMMENT '총 조회수',
  `bookmark_count` BIGINT NOT NULL DEFAULT 0 COMMENT '북마크 수',
  `uploader_id` BIGINT NOT NULL COMMENT '업로더',
  `access_level` VARCHAR(30) NOT NULL DEFAULT 'FREE' COMMENT '최소 요구 요금제 (FREE/BASIC/UPLUS)',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '컨텐츠 메타데이터 생성일',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '컨텐츠 메타데이터 수정일',

  PRIMARY KEY (`content_id`),

  CONSTRAINT `FK_contents_uploader_users`
    FOREIGN KEY (`uploader_id`)
    REFERENCES `users` (`user_id`)
    ON UPDATE RESTRICT
    ON DELETE RESTRICT

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 콘텐츠 태그 매핑 (content_tags)
CREATE TABLE `content_tags` (
  `content_tags_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '식별자',
  `content_id` BIGINT NOT NULL COMMENT '콘텐츠ID',
  `tag_id` BIGINT NOT NULL COMMENT '태그ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '매핑 생성 시각',

  PRIMARY KEY (`content_tags_id`),

  -- 매핑 중복 방지
  UNIQUE KEY `UQ_content_tags_content_tag` (`content_id`, `tag_id`),

  CONSTRAINT `FK_content_tags_contents`
    FOREIGN KEY (`content_id`)
    REFERENCES `contents` (`content_id`)
    ON UPDATE RESTRICT
    ON DELETE RESTRICT,

  CONSTRAINT `FK_content_tags_tags`
    FOREIGN KEY (`tag_id`)
    REFERENCES `tags` (`tag_id`)
    ON UPDATE RESTRICT
    ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 비디오 (videos)
CREATE TABLE `videos` (
  `video_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '비디오 ID',
  `content_id` BIGINT NOT NULL COMMENT '소속 작품 ID',
  `episode_no` INT NOT NULL COMMENT '회차 번호',
  `title` VARCHAR(200) NULL COMMENT '에피소드 제목',
  `description` TEXT NULL COMMENT '에피소드 설명',
  `thumbnail_url` VARCHAR(500) NULL COMMENT '에피소드 썸네일',
  `view_count` BIGINT NOT NULL DEFAULT 0 COMMENT '조회수',
  `status` VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '영상 상태',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일',

  PRIMARY KEY (`video_id`),

  -- 콘텐츠 내 회차 중복 방지
  UNIQUE KEY `UQ_videos_content_episode` (`content_id`, `episode_no`),

  CONSTRAINT `FK_videos_contents`
    FOREIGN KEY (`content_id`)
    REFERENCES `contents` (`content_id`)
    ON UPDATE RESTRICT
    ON DELETE RESTRICT

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 영상 파일 정보 (video_files)
CREATE TABLE `video_files` (
  `file_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '파일 ID',
  `video_id` BIGINT NOT NULL COMMENT '소속 비디오 ID',
  `original_url` VARCHAR(500) NULL COMMENT '원본 파일 경로 S3',
  `hls_url` VARCHAR(500) NULL COMMENT '스트리밍용 m3u8 경로',
  `duration_sec` INT NOT NULL DEFAULT 0 COMMENT '재생 시간 (초)',
  `transcode_status` VARCHAR(20) NOT NULL DEFAULT 'WAITING' COMMENT '트랜스코딩 상태',

  PRIMARY KEY (`file_id`),

  -- videos와 1:1 관계 보장
  UNIQUE KEY `UQ_video_files_video_id` (`video_id`),

  CONSTRAINT `FK_video_files_videos`
    FOREIGN KEY (`video_id`)
    REFERENCES `videos` (`video_id`)
    ON UPDATE RESTRICT
    ON DELETE RESTRICT

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 콘텐츠 지표 스냅샷 (content_metric_snapshots)
CREATE TABLE `content_metric_snapshots` (
  `bucket_start_at` DATETIME NOT NULL COMMENT '10분 단위 버킷 시작 시각',
  `content_id` BIGINT NOT NULL COMMENT '대상 콘텐츠ID',

  `snapshot_view_count` BIGINT NOT NULL DEFAULT 0 COMMENT '버킷 시점의 누적 조회수',
  `snapshot_bookmark_count` BIGINT NOT NULL DEFAULT 0 COMMENT '버킷 시점의 누적 북마크수',

  `delta_view_count` BIGINT NOT NULL DEFAULT 0 COMMENT '10분 조회수 증가분',
  `delta_bookmark_count` BIGINT NOT NULL DEFAULT 0 COMMENT '10분 북마크 증가분',
  `delta_completed_user_count` BIGINT NOT NULL DEFAULT 0 COMMENT '해당 10분 구간 user 수',

  `aggregated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '집계 실행 시각',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시(재집계/업서트 대비)',

  PRIMARY KEY (`bucket_start_at`, `content_id`),

  CONSTRAINT `FK_cms_contents`
    FOREIGN KEY (`content_id`)
    REFERENCES `contents` (`content_id`)
    ON UPDATE RESTRICT
    ON DELETE RESTRICT

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- =========================================================
-- 5. Engagement(상호작용)
-- =========================================================

-- 북마크 (bookmarks)
CREATE TABLE `bookmarks` (
  `bookmark_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '북마크 ID',
  `user_id` BIGINT NOT NULL COMMENT '회원 ID',
  `content_id` BIGINT NOT NULL COMMENT '콘텐츠 ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '찜한 시각',

  PRIMARY KEY (`bookmark_id`),

  -- 동일 유저의 동일 콘텐츠 중복 북마크 방지
  UNIQUE KEY `UQ_bookmarks_user_content` (`user_id`, `content_id`),

  CONSTRAINT `FK_bookmarks_users`
    FOREIGN KEY (`user_id`)
    REFERENCES `users` (`user_id`)
    ON UPDATE RESTRICT
    ON DELETE RESTRICT,

  CONSTRAINT `FK_bookmarks_contents`
    FOREIGN KEY (`content_id`)
    REFERENCES `contents` (`content_id`)
    ON UPDATE RESTRICT
    ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 댓글 (comments)
 CREATE TABLE `comments` (
  `comment_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '댓글ID',
  `video_id` BIGINT NOT NULL COMMENT '비디오 ID',
  `user_id` BIGINT NOT NULL COMMENT '작성자ID',
  `body` TEXT NOT NULL COMMENT '댓글 본문',
  `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '삭제 처리 상태',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '작성 시각',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시각',

  PRIMARY KEY (`comment_id`),

  CONSTRAINT `FK_comments_videos`
    FOREIGN KEY (`video_id`)
    REFERENCES `videos` (`video_id`)
    ON UPDATE RESTRICT
    ON DELETE RESTRICT,

  CONSTRAINT `FK_comments_users`
    FOREIGN KEY (`user_id`)
    REFERENCES `users` (`user_id`)
    ON UPDATE RESTRICT
    ON DELETE RESTRICT

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 시청 이력 (watch_histories)
CREATE TABLE `watch_histories` (
  `history_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '시청이력ID',
  `user_id` BIGINT NOT NULL COMMENT '사용자ID',
  `content_id` BIGINT NOT NULL COMMENT '작품 ID',
  `video_id` BIGINT NOT NULL COMMENT '영상 ID',
  `status` VARCHAR(20) NOT NULL DEFAULT 'STARTED' COMMENT '시청 상태',
  `last_position_sec` INT NOT NULL DEFAULT 0 COMMENT '마지막 재생 지점(초)',
  `last_watched_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `completed_at` DATETIME NULL COMMENT '영상 시청 완료 시각',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '최초 생성 시각',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '갱신 시각(시청 시마다 업데이트)',

  PRIMARY KEY (`history_id`),

  -- 사용자별 동일 video 중복 이력 방지 (1 row upsert 전제)
  UNIQUE KEY `UQ_watch_histories_user_video` (`user_id`, `video_id`),

  CONSTRAINT `FK_watch_histories_users`
    FOREIGN KEY (`user_id`)
    REFERENCES `users` (`user_id`)
    ON UPDATE RESTRICT
    ON DELETE RESTRICT,

  CONSTRAINT `FK_watch_histories_contents`
    FOREIGN KEY (`content_id`)
    REFERENCES `contents` (`content_id`)
    ON UPDATE RESTRICT
    ON DELETE RESTRICT,

  CONSTRAINT `FK_watch_histories_videos`
    FOREIGN KEY (`video_id`)
    REFERENCES `videos` (`video_id`)
    ON UPDATE RESTRICT
    ON DELETE RESTRICT

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

  
  
