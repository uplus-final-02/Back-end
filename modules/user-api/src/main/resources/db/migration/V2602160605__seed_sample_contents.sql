-- V2602160605__seed_sample_contents.sql
-- 목적:
-- 검색/인덱싱 테스트를 위한 샘플 콘텐츠 데이터 적재

-- 1) 업로더 사용자 1명 생성 (중복 방지)
INSERT INTO `users` (`nickname`, `user_role`, `user_status`)
SELECT 'sample_uploader', 'USER', 'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1
    FROM `users`
    WHERE `nickname` = 'sample_uploader'
);

-- 2) 업로더 ID 확보
SET @sample_uploader_id := (
    SELECT `user_id`
    FROM `users`
    WHERE `nickname` = 'sample_uploader'
    ORDER BY `user_id` DESC
    LIMIT 1
);

-- 3) 샘플 콘텐츠 5건 생성
INSERT INTO `contents` (
    `type`,
    `title`,
    `description`,
    `thumbnail_url`,
    `status`,
    `uploader_id`,
    `access_level`
) VALUES
    ('SINGLE', '테스트 드라마 1화', JSON_OBJECT('summary', '감동적인 가족 이야기'), 'sample-thumb-1.jpg', 'ACTIVE', @sample_uploader_id, 'FREE'),
    ('SERIES', '액션 히어로 시즌1', JSON_OBJECT('summary', '도시를 지키는 히어로 액션'), 'sample-thumb-2.jpg', 'ACTIVE', @sample_uploader_id, 'BASIC'),
    ('SINGLE', '음악 콘서트 라이브', JSON_OBJECT('summary', '실황 공연과 인터뷰'), 'sample-thumb-3.jpg', 'ACTIVE', @sample_uploader_id, 'FREE'),
    ('SERIES', '다큐멘터리 한국사', JSON_OBJECT('summary', '역사 흐름을 쉽게 풀어낸 시리즈'), 'sample-thumb-4.jpg', 'ACTIVE', @sample_uploader_id, 'FREE'),
    ('SINGLE', '코미디 쇼 특집', JSON_OBJECT('summary', '주말 예능 코미디 특집 편성'), 'sample-thumb-5.jpg', 'ACTIVE', @sample_uploader_id, 'BASIC');
