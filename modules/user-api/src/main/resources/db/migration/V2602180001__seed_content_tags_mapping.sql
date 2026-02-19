-- V2602180001__seed_content_tags_mapping.sql
-- 내용: 제목과 태그명을 매칭하여 content_tags 테이블에 데이터 삽입

-- 1. '테스트 드라마 1화' (드라마, 가족) -> 태그명은 DB에 있는 것 기준(드라마, 영화 등)
INSERT IGNORE INTO content_tags (content_id, tag_id)
SELECT c.content_id, t.tag_id
FROM contents c
JOIN tags t ON t.name IN ('영화', '예능') -- 예시: 있는 태그로 매핑
WHERE c.title = '테스트 드라마 1화';

-- 2. '액션 히어로 시즌1' (영화, 게임)
INSERT IGNORE INTO content_tags (content_id, tag_id)
SELECT c.content_id, t.tag_id
FROM contents c
JOIN tags t ON t.name IN ('영화', '게임')
WHERE c.title = '액션 히어로 시즌1';

-- 3. '음악 콘서트 라이브' (음악, 예능)
INSERT IGNORE INTO content_tags (content_id, tag_id)
SELECT c.content_id, t.tag_id
FROM contents c
JOIN tags t ON t.name IN ('음악', '예능')
WHERE c.title = '음악 콘서트 라이브';

-- 4. '다큐멘터리 한국사' (뉴스, 영화)
INSERT IGNORE INTO content_tags (content_id, tag_id)
SELECT c.content_id, t.tag_id
FROM contents c
JOIN tags t ON t.name IN ('뉴스', '영화')
WHERE c.title = '다큐멘터리 한국사';

-- 5. '코미디 쇼 특집' (예능)
INSERT IGNORE INTO content_tags (content_id, tag_id)
SELECT c.content_id, t.tag_id
FROM contents c
JOIN tags t ON t.name IN ('예능')
WHERE c.title = '코미디 쇼 특집';

-- 6. '무빙' (영화) - 추가 데이터
INSERT IGNORE INTO content_tags (content_id, tag_id)
SELECT c.content_id, t.tag_id
FROM contents c
JOIN tags t ON t.name IN ('영화')
WHERE c.title = '무빙';