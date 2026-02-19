-- V2602190001__contents_uploader_nullable.sql
-- contents 테이블의 uploader_id 컬럼을 NULL 허용으로 변경 (NULL이면 관리자 업로드)

ALTER TABLE `contents`
MODIFY COLUMN `uploader_id` BIGINT NULL COMMENT '업로더 (NULL이면 관리자)';
