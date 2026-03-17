-- 크리에이터 영상 썸네일 업로드 기능 추가로 thumbnail_url 컬럼 재추가
ALTER TABLE user_contents
    ADD COLUMN thumbnail_url VARCHAR(500) NULL COMMENT '썸네일 URL' AFTER description;