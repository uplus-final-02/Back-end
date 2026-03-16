-- 1) 유저 업로드 콘텐츠
CREATE TABLE `user_contents` (
                                 `content_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '유저 업로드 콘텐츠 ID',
                                 `parent_content_id` BIGINT NOT NULL COMMENT '관리자 콘텐츠 ID(선택한 원본)',
                                 `title` VARCHAR(200) NOT NULL COMMENT '유저 업로드 제목(또는 TEMP)',
                                 `description` JSON NULL COMMENT '설명 데이터',
                                 `thumbnail_url` VARCHAR(500) NOT NULL COMMENT '썸네일 URL(또는 TEMP)',

                                 `content_status` VARCHAR(20) NOT NULL DEFAULT 'HIDDEN'
                                     COMMENT '콘텐츠 상태(ContentStatus: ACTIVE/HIDDEN/DELETED)',

                                 `total_view_count` BIGINT NOT NULL DEFAULT 0 COMMENT '총 조회수',
                                 `bookmark_count` BIGINT NOT NULL DEFAULT 0 COMMENT '북마크 수',

                                 `uploader_id` BIGINT NOT NULL COMMENT '업로더(유저)',
                                 `access_level` VARCHAR(30) NOT NULL DEFAULT 'FREE' COMMENT '최소 요구 요금제(FREE/BASIC/UPLUS)',

                                 `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일',
                                 `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일',

                                 PRIMARY KEY (`content_id`),

                                 CONSTRAINT `FK_user_contents_parent_contents`
                                     FOREIGN KEY (`parent_content_id`)
                                         REFERENCES `contents` (`content_id`)
                                         ON UPDATE RESTRICT
                                         ON DELETE RESTRICT,

                                 CONSTRAINT `FK_user_contents_uploader_users`
                                     FOREIGN KEY (`uploader_id`)
                                         REFERENCES `users` (`user_id`)
                                         ON UPDATE RESTRICT
                                         ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX `idx_user_contents_parent_content_id`
    ON `user_contents` (`parent_content_id`);

CREATE INDEX `idx_user_contents_uploader_id_created_at`
    ON `user_contents` (`uploader_id`, `created_at`);


-- 2) 유저 업로드 영상 파일(유저 콘텐츠에 1:1)
CREATE TABLE `user_video_files` (
                                    `file_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '유저 업로드 파일 ID',
                                    `content_id` BIGINT NOT NULL COMMENT '소속 유저 콘텐츠 ID',

                                    `original_url` VARCHAR(500) NULL COMMENT '원본 파일 경로(S3 objectKey)',
                                    `hls_url` VARCHAR(500) NULL COMMENT '스트리밍 m3u8 경로',
                                    `duration_sec` INT NOT NULL DEFAULT 0 COMMENT '재생 시간(초)',

                                    `video_status` VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
                                        COMMENT '영상 상태(VideoStatus: DRAFT/PUBLIC/PRIVATE)',

                                    `transcode_status` VARCHAR(20) NOT NULL DEFAULT 'PENDING_UPLOAD'
                                        COMMENT '트랜스코딩 상태(TranscodeStatus)',

                                    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일',
                                    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일',

                                    PRIMARY KEY (`file_id`),

                                    UNIQUE KEY `UQ_user_video_files_content_id` (`content_id`),

                                    CONSTRAINT `FK_user_video_files_user_contents`
                                        FOREIGN KEY (`content_id`)
                                            REFERENCES `user_contents` (`content_id`)
                                            ON UPDATE RESTRICT
                                            ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;