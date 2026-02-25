-- V2602241720__dumpData.sql
-- users
--   auth_accounts - 모든 비밀번호 : test12345678
--   bookmarks
--   comments
--   contents
--   tags
--   user_preferred_tags
--   content_tags
--   video_files
--   videos
--   watch_histories

-- 외래 키 무결성 체크 해제 (TRUNCATE 오류 방지)
SET FOREIGN_KEY_CHECKS = 0;

-- 1. TRUNCATE TABLE 진행
TRUNCATE TABLE watch_histories;
TRUNCATE TABLE comments;
TRUNCATE TABLE bookmarks;
TRUNCATE TABLE video_files;
TRUNCATE TABLE videos;
TRUNCATE TABLE content_tags;
TRUNCATE TABLE contents;
TRUNCATE TABLE user_preferred_tags;
TRUNCATE TABLE tags;
TRUNCATE TABLE auth_accounts;
TRUNCATE TABLE users;

-- 2. INSERT 데이터 삽입

-- [1] users (회원 정보) - user_id 1만 ADMIN
INSERT INTO users (user_id, nickname, profile_image, user_role, user_status, created_at, updated_at) VALUES
(1, 'test1', 'profiles/1/img.jpg', 'ADMIN', 'ACTIVE', NOW(), NOW()),
(2, 'test2', 'profiles/2/img.jpg', 'USER', 'ACTIVE', NOW(), NOW()),
(3, 'test3', 'profiles/3/img.jpg', 'USER', 'ACTIVE', NOW(), NOW()),
(4, 'test4', 'profiles/4/img.jpg', 'USER', 'ACTIVE', NOW(), NOW()),
(5, 'test5', 'profiles/5/img.jpg', 'USER', 'ACTIVE', NOW(), NOW()),
(6, 'test6', 'profiles/6/img.jpg', 'USER', 'INACTIVE', NOW(), NOW()),
(7, 'test7', 'profiles/7/img.jpg', 'USER', 'ACTIVE', NOW(), NOW()),
(8, 'test8', 'profiles/8/img.jpg', 'USER', 'ACTIVE', NOW(), NOW()),
(9, 'test9', 'profiles/9/img.jpg', 'USER', 'ACTIVE', NOW(), NOW()),
(10, 'test10', 'profiles/10/img.jpg', 'USER', 'DELETED', NOW(), NOW());

-- [2] auth_accounts (로그인 수단) - 전부 EMAIL
INSERT INTO auth_accounts (auth_account_id, user_id, auth_provider, auth_provider_subject, email, password_hash, created_at) VALUES
(1, 1, 'EMAIL', 'test1@example.com', 'test1@example.com', '$2a$10$I.MLXBOlJ2G4SckoFzI7xuLw8Leif4Xr/bwK23WkGvyc8e4cWca.2', NOW()),
(2, 2, 'EMAIL', 'test2@example.com', 'test2@example.com', '$2a$10$I.MLXBOlJ2G4SckoFzI7xuLw8Leif4Xr/bwK23WkGvyc8e4cWca.2', NOW()),
(3, 3, 'EMAIL', 'test3@example.com', 'test3@example.com', '$2a$10$I.MLXBOlJ2G4SckoFzI7xuLw8Leif4Xr/bwK23WkGvyc8e4cWca.2', NOW()),
(4, 4, 'EMAIL', 'test4@example.com', 'test4@example.com', '$2a$10$I.MLXBOlJ2G4SckoFzI7xuLw8Leif4Xr/bwK23WkGvyc8e4cWca.2', NOW()),
(5, 5, 'EMAIL', 'test5@example.com', 'test5@example.com', '$2a$10$I.MLXBOlJ2G4SckoFzI7xuLw8Leif4Xr/bwK23WkGvyc8e4cWca.2', NOW()),
(6, 6, 'EMAIL', 'test6@example.com', 'test6@example.com', '$2a$10$I.MLXBOlJ2G4SckoFzI7xuLw8Leif4Xr/bwK23WkGvyc8e4cWca.2', NOW()),
(7, 7, 'EMAIL', 'test7@example.com', 'test7@example.com', '$2a$10$I.MLXBOlJ2G4SckoFzI7xuLw8Leif4Xr/bwK23WkGvyc8e4cWca.2', NOW()),
(8, 8, 'EMAIL', 'test8@example.com', 'test8@example.com', '$2a$10$I.MLXBOlJ2G4SckoFzI7xuLw8Leif4Xr/bwK23WkGvyc8e4cWca.2', NOW()),
(9, 9, 'EMAIL', 'test9@example.com', 'test9@example.com', '$2a$10$I.MLXBOlJ2G4SckoFzI7xuLw8Leif4Xr/bwK23WkGvyc8e4cWca.2', NOW()),
(10, 10, 'EMAIL', 'test10@example.com', 'test10@example.com', '$2a$10$I.MLXBOlJ2G4SckoFzI7xuLw8Leif4Xr/bwK23WkGvyc8e4cWca.2', NOW());

-- [3] tags (콘텐츠 태그 마스터)
INSERT INTO tags (tag_id, name, is_active, created_at, type) VALUES
(1, '액션', TRUE, NOW(), 'GENRE'),
(2, '로맨스', TRUE, NOW(), 'GENRE'),
(3, '스릴러', TRUE, NOW(), 'GENRE'),
(4, '코미디', TRUE, NOW(), 'GENRE'),
(5, 'SF', TRUE, NOW(), 'GENRE'),
(6, '판타지', TRUE, NOW(), 'GENRE'),
(7, '영화', TRUE, NOW(), 'CATEGORY'),
(8, 'TV드라마', TRUE, NOW(), 'CATEGORY'),
(9, '예능', TRUE, NOW(), 'CATEGORY'),
(10, '다큐멘터리', TRUE, NOW(), 'CATEGORY');

-- [4] user_preferred_tags (유저 선호 태그)
INSERT INTO user_preferred_tags (user_preferred_tags_id, user_id, tag_id, created_at) VALUES
(1, 1, 1, NOW()), (2, 1, 5, NOW()), (3, 2, 2, NOW()), (4, 3, 4, NOW()), (5, 4, 2, NOW()),
(6, 5, 3, NOW()), (7, 6, 9, NOW()), (8, 7, 1, NOW()), (9, 8, 10, NOW()), (10, 9, 7, NOW());

-- [5] contents (콘텐츠 메타데이터)
-- id=1: 오징어 게임 (유일한 SERIES)
-- id=2~6: 남은 영화들 (ADMIN 업로드, uploader_id=1)
-- id=7~15: USER 업로드 싱글 영화 9개 (uploader_id=2~10)
INSERT INTO contents (content_id, type, title, description, thumbnail_url, status, total_view_count, bookmark_count, uploader_id, access_level, created_at, updated_at) VALUES
(1, 'SERIES', '오징어 게임', '{"summary": "456억원 상금이 걸린 서바이벌", "release": "2021-09-17", "director": "황동혁", "actor": "이정재, 박해수, 정호연"}', 'https://thumb.abcd/squid.jpg', 'ACTIVE', 150000, 1500, 1, 'BASIC', NOW(), NOW()),
(2, 'SINGLE', '기생충', '{"summary": "반지하 가족과 부잣집 가족의 만남", "release": "2019-05-30", "director": "봉준호", "actor": "송강호, 이선균, 조여정"}', 'https://thumb.abcd/parasite.jpg', 'ACTIVE', 20000, 300, 1, 'FREE', NOW(), NOW()),
(3, 'SINGLE', '범죄도시', '{"summary": "괴물형사 마석도의 소탕 작전", "release": "2017-10-03", "director": "강윤성", "actor": "마동석, 윤계상"}', 'https://thumb.abcd/crime.jpg', 'ACTIVE', 12000, 100, 1, 'BASIC', NOW(), NOW()),
(4, 'SINGLE', '인터스텔라', '{"summary": "우주로 향한 인류의 희망을 찾아서", "release": "2014-11-06", "director": "크리스토퍼 놀란", "actor": "매튜 맥커너히, 앤 해서웨이"}', 'https://thumb.abcd/inter.jpg', 'ACTIVE', 30000, 600, 1, 'BASIC', NOW(), NOW()),
(5, 'SINGLE', '어바웃 타임', '{"summary": "시간 여행을 할 수 있는 남자의 로맨스", "release": "2013-12-05", "director": "리차드 커티스", "actor": "도姆놀 글리슨, 레이첼 맥아담스"}', 'https://thumb.abcd/about.jpg', 'ACTIVE', 9500, 80, 1, 'FREE', NOW(), NOW()),
(6, 'SINGLE', '극한직업', '{"summary": "낮에는 치킨장사, 밤에는 잠복근무", "release": "2019-01-23", "director": "이병헌", "actor": "류승룡, 이하늬, 진선규"}', 'https://thumb.abcd/extreme.jpg', 'ACTIVE', 22000, 350, 1, 'FREE', NOW(), NOW()),
(7, 'SINGLE', '아바타: 물의 길', '{"summary": "판도라 행성의 새로운 위협", "release": "2022-12-14", "director": "제임스 카메론", "actor": "조 샐다나, 샘 워싱턴"}', 'https://thumb.abcd/avatar.jpg', 'ACTIVE', 11000, 200, 2, 'UPLUS', NOW(), NOW()),
(8, 'SINGLE', '타이타닉', '{"summary": "침몰하는 배 위에서의 세기의 로맨스", "release": "1998-02-20", "director": "제임스 카메론", "actor": "레오나르도 디카프리오, 케이트 윈슬렛"}', 'https://thumb.abcd/titanic.jpg', 'ACTIVE', 18000, 450, 3, 'BASIC', NOW(), NOW()),
(9, 'SINGLE', '인셉션', '{"summary": "타인의 꿈속에 침투해 생각을 훔친다", "release": "2010-07-21", "director": "크리스토퍼 놀란", "actor": "레오나르도 디카프리오, 조셉 고든 레빗"}', 'https://thumb.abcd/inception.jpg', 'ACTIVE', 25000, 500, 4, 'BASIC', NOW(), NOW()),
(10, 'SINGLE', '매트릭스', '{"summary": "인류를 지배하는 가상현실 속에서의 각성", "release": "1999-05-15", "director": "워쇼스키 형제", "actor": "키아누 리브스, 캐리앤 모스"}', 'https://thumb.abcd/matrix.jpg', 'ACTIVE', 14000, 300, 5, 'FREE', NOW(), NOW()),
(11, 'SINGLE', '다크 나이트', '{"summary": "배트맨과 조커의 최후의 대결", "release": "2008-08-06", "director": "크리스토퍼 놀란", "actor": "크리스찬 베일, 히스 레저"}', 'https://thumb.abcd/darkknight.jpg', 'ACTIVE', 32000, 700, 6, 'BASIC', NOW(), NOW()),
(12, 'SINGLE', '반지의 제왕', '{"summary": "절대반지를 파괴하기 위한 여정", "release": "2001-12-31", "director": "피터 잭슨", "actor": "일라이저 우드, 이안 맥켈런"}', 'https://thumb.abcd/lotr.jpg', 'ACTIVE', 28000, 650, 7, 'UPLUS', NOW(), NOW()),
(13, 'SINGLE', '어벤져스', '{"summary": "지구를 구하기 위한 슈퍼히어로들의 결성", "release": "2012-04-26", "director": "조스 웨던", "actor": "로버트 다우니 주니어, 크리스 에반스"}', 'https://thumb.abcd/avengers.jpg', 'ACTIVE', 40000, 800, 8, 'BASIC', NOW(), NOW()),
(14, 'SINGLE', '해리포터', '{"summary": "마법학교 호그와트에서의 모험", "release": "2001-12-14", "director": "크리스 콜럼버스", "actor": "다니엘 래드클리프, 엠마 왓슨"}', 'https://thumb.abcd/harry.jpg', 'ACTIVE', 35000, 750, 9, 'BASIC', NOW(), NOW()),
(15, 'SINGLE', '스파이더맨', '{"summary": "친절한 이웃 스파이더맨의 탄생", "release": "2002-05-03", "director": "샘 레이미", "actor": "토비 맥과이어, 커스틴 던스트"}', 'https://thumb.abcd/spider.jpg', 'ACTIVE', 21000, 400, 10, 'FREE', NOW(), NOW());

-- [6] content_tags (콘텐츠 - 태그 매핑)
INSERT INTO content_tags (content_tags_id, content_id, tag_id, created_at) VALUES
(1, 1, 3, NOW()), (2, 2, 7, NOW()), (3, 3, 1, NOW()), (4, 4, 5, NOW()),
(5, 5, 2, NOW()), (6, 6, 4, NOW()), (7, 7, 5, NOW()), (8, 8, 2, NOW()),
(9, 9, 5, NOW()), (10, 10, 5, NOW()), (11, 11, 1, NOW()), (12, 12, 6, NOW()),
(13, 13, 1, NOW()), (14, 14, 6, NOW()), (15, 15, 1, NOW());

-- [7] videos (비디오 에피소드 정보)
-- video 1~10: 오징어 게임 1화~10화 (content_id = 1)
-- video 11~24: 나머지 영화들의 본편 (content_id 2~15)
INSERT INTO videos (video_id, content_id, episode_no, title, description, thumbnail_url, view_count, status) VALUES
(1, 1, 1, '무궁화 꽃이 피던 날', '오징어 게임 1화', 'https://thumb.abcd/sq_1.jpg', 15000, 'PUBLIC'),
(2, 1, 2, '지옥', '오징어 게임 2화', 'https://thumb.abcd/sq_2.jpg', 14500, 'PUBLIC'),
(3, 1, 3, '우산을 쓴 남자', '오징어 게임 3화', 'https://thumb.abcd/sq_3.jpg', 14000, 'PUBLIC'),
(4, 1, 4, '쫄려도 편먹기', '오징어 게임 4화', 'https://thumb.abcd/sq_4.jpg', 13500, 'PUBLIC'),
(5, 1, 5, '평등한 세상', '오징어 게임 5화', 'https://thumb.abcd/sq_5.jpg', 13000, 'PUBLIC'),
(6, 1, 6, '깐부', '오징어 게임 6화', 'https://thumb.abcd/sq_6.jpg', 16000, 'PUBLIC'),
(7, 1, 7, 'V.I.PS', '오징어 게임 7화', 'https://thumb.abcd/sq_7.jpg', 14000, 'PUBLIC'),
(8, 1, 8, '프론트맨', '오징어 게임 8화', 'https://thumb.abcd/sq_8.jpg', 15000, 'PUBLIC'),
(9, 1, 9, '운수 좋은 날', '오징어 게임 9화', 'https://thumb.abcd/sq_9.jpg', 16500, 'PUBLIC'),
(10, 1, 10, '새로운 시작', '오징어 게임 10화 (가상)', 'https://thumb.abcd/sq_10.jpg', 18500, 'PUBLIC'),
(11, 2, 1, '기생충 본편', '기생충 영화 본편', 'https://thumb.abcd/para_v.jpg', 20000, 'PUBLIC'),
(12, 3, 1, '범죄도시 본편', '범죄도시 영화 본편', 'https://thumb.abcd/crime_v.jpg', 12000, 'PUBLIC'),
(13, 4, 1, '인터스텔라 본편', '우주 탐험 본편', 'https://thumb.abcd/inter_v.jpg', 30000, 'PUBLIC'),
(14, 5, 1, '어바웃 타임 본편', '시간 여행 로맨스', 'https://thumb.abcd/about_v.jpg', 9500, 'PUBLIC'),
(15, 6, 1, '극한직업 본편', '수원왕갈비통닭', 'https://thumb.abcd/extreme_v.jpg', 22000, 'PUBLIC'),
(16, 7, 1, '아바타 물의길 본편', '아바타 2 본편', 'https://thumb.abcd/avatar_v.jpg', 11000, 'PUBLIC'),
(17, 8, 1, '타이타닉 본편', '타이타닉 영화 본편', 'https://thumb.abcd/titanic_v.jpg', 18000, 'PUBLIC'),
(18, 9, 1, '인셉션 본편', '인셉션 영화 본편', 'https://thumb.abcd/incept_v.jpg', 25000, 'PUBLIC'),
(19, 10, 1, '매트릭스 본편', '매트릭스 영화 본편', 'https://thumb.abcd/matrix_v.jpg', 14000, 'PUBLIC'),
(20, 11, 1, '다크 나이트 본편', '다크 나이트 본편', 'https://thumb.abcd/dark_v.jpg', 32000, 'PUBLIC'),
(21, 12, 1, '반지의 제왕 본편', '반지의 제왕 본편', 'https://thumb.abcd/lotr_v.jpg', 28000, 'PUBLIC'),
(22, 13, 1, '어벤져스 본편', '어벤져스 영화 본편', 'https://thumb.abcd/aven_v.jpg', 40000, 'PUBLIC'),
(23, 14, 1, '해리포터 본편', '해리포터 1편', 'https://thumb.abcd/harry_v.jpg', 35000, 'PUBLIC'),
(24, 15, 1, '스파이더맨 본편', '스파이더맨 영화 본편', 'https://thumb.abcd/spider_v.jpg', 21000, 'PUBLIC');

-- [8] video_files (실제 스트리밍 파일 경로) 1:1 대응
INSERT INTO video_files (file_id, video_id, original_url, hls_url, duration_sec, transcode_status) VALUES
(1, 1, 's3://bucket/raw/sq_1.mp4', 's3://bucket/hls/sq_1.m3u8', 3600, 'DONE'),
(2, 2, 's3://bucket/raw/sq_2.mp4', 's3://bucket/hls/sq_2.m3u8', 3500, 'DONE'),
(3, 3, 's3://bucket/raw/sq_3.mp4', 's3://bucket/hls/sq_3.m3u8', 3400, 'DONE'),
(4, 4, 's3://bucket/raw/sq_4.mp4', 's3://bucket/hls/sq_4.m3u8', 3600, 'DONE'),
(5, 5, 's3://bucket/raw/sq_5.mp4', 's3://bucket/hls/sq_5.m3u8', 3700, 'DONE'),
(6, 6, 's3://bucket/raw/sq_6.mp4', 's3://bucket/hls/sq_6.m3u8', 3900, 'DONE'),
(7, 7, 's3://bucket/raw/sq_7.mp4', 's3://bucket/hls/sq_7.m3u8', 3500, 'DONE'),
(8, 8, 's3://bucket/raw/sq_8.mp4', 's3://bucket/hls/sq_8.m3u8', 3450, 'DONE'),
(9, 9, 's3://bucket/raw/sq_9.mp4', 's3://bucket/hls/sq_9.m3u8', 3600, 'DONE'),
(10, 10, 's3://bucket/raw/sq_10.mp4', 's3://bucket/hls/sq_10.m3u8', 4000, 'DONE'),
(11, 11, 's3://bucket/raw/para.mp4', 's3://bucket/hls/para.m3u8', 7200, 'DONE'),
(12, 12, 's3://bucket/raw/crime.mp4', 's3://bucket/hls/crime.m3u8', 6800, 'DONE'),
(13, 13, 's3://bucket/raw/inter.mp4', 's3://bucket/hls/inter.m3u8', 10200, 'DONE'),
(14, 14, 's3://bucket/raw/about.mp4', 's3://bucket/hls/about.m3u8', 7400, 'DONE'),
(15, 15, 's3://bucket/raw/extreme.mp4', 's3://bucket/hls/extreme.m3u8', 6500, 'DONE'),
(16, 16, 's3://bucket/raw/avatar.mp4', 's3://bucket/hls/avatar.m3u8', 11500, 'DONE'),
(17, 17, 's3://bucket/raw/titanic.mp4', 's3://bucket/hls/titanic.m3u8', 11700, 'DONE'),
(18, 18, 's3://bucket/raw/incept.mp4', 's3://bucket/hls/incept.m3u8', 8800, 'DONE'),
(19, 19, 's3://bucket/raw/matrix.mp4', 's3://bucket/hls/matrix.m3u8', 8100, 'DONE'),
(20, 20, 's3://bucket/raw/dark.mp4', NULL, 9100, 'PROCESSING'),
(21, 21, 's3://bucket/raw/lotr.mp4', 's3://bucket/hls/lotr.m3u8', 10600, 'DONE'),
(22, 22, 's3://bucket/raw/aven.mp4', 's3://bucket/hls/aven.m3u8', 8500, 'DONE'),
(23, 23, 's3://bucket/raw/harry.mp4', 's3://bucket/hls/harry.m3u8', 9100, 'DONE'),
(24, 24, 's3://bucket/raw/spider.mp4', 's3://bucket/hls/spider.m3u8', 7200, 'DONE');

-- [9] bookmarks (콘텐츠 찜 내역)
INSERT INTO bookmarks (bookmark_id, user_id, content_id, created_at) VALUES
(1, 1, 1, NOW()), (2, 2, 11, NOW()), (3, 3, 15, NOW()), (4, 4, 12, NOW()),
(5, 5, 8, NOW()), (6, 6, 9, NOW()), (7, 7, 2, NOW()), (8, 8, 4, NOW()),
(9, 9, 13, NOW()), (10, 10, 14, NOW()), (11, 1, 2, NOW()), (12, 1, 3, NOW()),
(13, 2, 1, NOW()), (14, 2, 4, NOW()), (15, 3, 5, NOW()), (16, 3, 6, NOW()),
(17, 4, 7, NOW()), (18, 4, 8, NOW()), (19, 5, 1, NOW()), (20, 5, 10, NOW()),
(21, 6, 11, NOW()), (22, 6, 12, NOW()), (23, 7, 13, NOW()), (24, 7, 14, NOW()),
(25, 8, 15, NOW()), (26, 8, 1, NOW()), (27, 9, 2, NOW()), (28, 9, 3, NOW()),
(29, 10, 4, NOW()), (30, 10, 5, NOW());

-- [10] comments (영상 댓글)
INSERT INTO comments (comment_id, video_id, user_id, body, status, created_at, updated_at) VALUES
(1, 1, 2, '첫 화부터 몰입감 대박', 'ACTIVE', NOW(), NOW()),
(2, 6, 3, '깐부 에피소드는 진짜 눈물남', 'ACTIVE', NOW(), NOW()),
(3, 10, 4, '시즌2가 너무 기대됩니다', 'ACTIVE', NOW(), NOW()),
(4, 11, 5, '명작 영화 인정합니다', 'ACTIVE', NOW(), NOW()),
(5, 12, 6, '액션 시원하네요', 'ACTIVE', NOW(), NOW()),
(6, 13, 7, '우주 장면 웅장합니다', 'ACTIVE', NOW(), NOW()),
(7, 16, 8, 'CG 스케일 미쳤어요', 'ACTIVE', NOW(), NOW()),
(8, 20, 9, '조커 연기 소름 돋음', 'ACTIVE', NOW(), NOW()),
(9, 22, 10, '어셈블!', 'ACTIVE', NOW(), NOW()),
(10, 23, 1, '추억의 해리포터', 'ACTIVE', NOW(), NOW()),
(11, 1, 3, '다시 봐도 재밌네요 ㅋㅋ', 'ACTIVE', NOW(), NOW()),
(12, 2, 4, '진짜 너무 무서웠어요', 'ACTIVE', NOW(), NOW()),
(13, 3, 5, '달고나 게임 나도 해보고 싶다', 'ACTIVE', NOW(), NOW()),
(14, 4, 6, '줄다리기 전략 소름', 'ACTIVE', NOW(), NOW()),
(15, 5, 7, 'VIP들 진짜 얄미움', 'ACTIVE', NOW(), NOW()),
(16, 7, 8, '유리다리 너무 쫄깃해', 'ACTIVE', NOW(), NOW()),
(17, 8, 9, '프론트맨 정체 충격', 'ACTIVE', NOW(), NOW()),
(18, 9, 10, '결말이 어떻게 될지 ㄷㄷ', 'ACTIVE', NOW(), NOW()),
(19, 11, 1, '송강호 연기 미쳤다', 'ACTIVE', NOW(), NOW()),
(20, 12, 2, '진실의 방으로', 'ACTIVE', NOW(), NOW()),
(21, 14, 3, '최고의 로맨스 영화', 'ACTIVE', NOW(), NOW()),
(22, 15, 4, '지금까지 이런 맛은 없었다', 'ACTIVE', NOW(), NOW()),
(23, 17, 5, '내 마음속 영원한 1위', 'ACTIVE', NOW(), NOW()),
(24, 18, 6, '팽이는 과연 멈췄을까', 'ACTIVE', NOW(), NOW()),
(25, 19, 7, '빨간약 파란약', 'ACTIVE', NOW(), NOW()),
(26, 21, 8, '프로도 수고했다', 'ACTIVE', NOW(), NOW()),
(27, 22, 9, '타노스 포스 덜덜', 'ACTIVE', NOW(), NOW()),
(28, 23, 10, '입학통지서 언제 오나요', 'ACTIVE', NOW(), NOW()),
(29, 24, 2, '초대 스파이더맨 꿀잼', 'ACTIVE', NOW(), NOW()),
(30, 13, 1, '머피의 법칙', 'ACTIVE', NOW(), NOW());

-- [11] watch_histories (시청 이력)
INSERT INTO watch_histories (history_id, user_id, content_id, video_id, status, last_position_sec, last_watched_at, completed_at, created_at, updated_at) VALUES
(1, 1, 1, 6, 'COMPLETED', 3900, '2026-02-11 14:55:10', '2026-02-11 15:10:00', NOW(), NOW()),
(2, 2, 1, 10, 'WATCHING', 1200, '2026-02-11 14:55:10', NULL, '2026-02-11 14:55:10', '2026-02-11 14:55:10'),
(3, 3, 2, 11, 'COMPLETED', 7200, NOW(), NOW(), '2026-02-11 14:55:10', NOW()),
(4, 4, 3, 12, 'WATCHING', 500, NOW(), NULL, NOW(), NOW()),
(5, 5, 4, 13, 'COMPLETED', 10200, NOW(), NOW(), NOW(), NOW()),
(6, 6, 7, 16, 'WATCHING', 1500, NOW(), NULL, NOW(), NOW()),
(7, 7, 8, 17, 'COMPLETED', 11700, NOW(), NOW(), NOW(), NOW()),
(8, 8, 11, 20, 'STARTED', 10, NOW(), NULL, NOW(), NOW()),
(9, 9, 13, 22, 'COMPLETED', 8500, NOW(), NOW(), NOW(), NOW()),
(10, 10, 14, 23, 'WATCHING', 2100, NOW(), NULL, NOW(), NOW()),
(11, 1, 2, 11, 'WATCHING', 3000, NOW(), NULL, NOW(), NOW()),
(12, 1, 3, 12, 'COMPLETED', 6800, NOW(), NOW(), NOW(), NOW()),
(13, 2, 4, 13, 'STARTED', 50, NOW(), NULL, NOW(), NOW()),
(14, 2, 5, 14, 'WATCHING', 4000, NOW(), NULL, NOW(), NOW()),
(15, 3, 6, 15, 'COMPLETED', 6500, NOW(), NOW(), NOW(), NOW()),
(16, 3, 1, 1, 'COMPLETED', 3600, NOW(), NOW(), NOW(), NOW()),
(17, 4, 1, 2, 'COMPLETED', 3500, NOW(), NOW(), NOW(), NOW()),
(18, 4, 1, 3, 'WATCHING', 1800, NOW(), NULL, NOW(), NOW()),
(19, 5, 9, 18, 'WATCHING', 5000, NOW(), NULL, NOW(), NOW()),
(20, 5, 10, 19, 'COMPLETED', 8100, NOW(), NOW(), NOW(), NOW()),
(21, 6, 1, 4, 'STARTED', 10, NOW(), NULL, NOW(), NOW()),
(22, 6, 1, 5, 'WATCHING', 2000, NOW(), NULL, NOW(), NOW()),
(23, 7, 12, 21, 'COMPLETED', 10600, NOW(), NOW(), NOW(), NOW()),
(24, 7, 15, 24, 'WATCHING', 3600, NOW(), NULL, NOW(), NOW()),
(25, 8, 1, 7, 'COMPLETED', 3500, NOW(), NOW(), NOW(), NOW()),
(26, 8, 1, 8, 'WATCHING', 1200, NOW(), NULL, NOW(), NOW()),
(27, 9, 5, 14, 'STARTED', 5, NOW(), NULL, NOW(), NOW()),
(28, 9, 6, 15, 'COMPLETED', 6500, NOW(), NOW(), NOW(), NOW()),
(29, 10, 1, 9, 'COMPLETED', 3600, NOW(), NOW(), NOW(), NOW()),
(30, 10, 1, 10, 'WATCHING', 2500, NOW(), NULL, NOW(), NOW());

-- 외래 키 무결성 체크 복구
SET FOREIGN_KEY_CHECKS = 1;

ALTER TABLE watch_histories
ADD COLUMN deleted_at DATETIME NULL COMMENT '삭제일자 (Soft Delete)';