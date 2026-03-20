-- 샘플 데이터 추가

-- 3개의 컨텐츠, 12개의 비디오&비디오파일, 3개의 시청이력

-- 첫번째 컨텐츠는 시리즈, 비디오 10개
-- 두번째 컨텐츠는 단편 영화, 비디오 1개
-- 세번째 컨텐츠는 사용자업로드 동영상, 비디오 1개

-- 모두 1번 사용자가 업로드 했다고 가정
INSERT INTO contents
(`type`, `title`, `thumbnail_url`, `status`, `uploader_id`, `access_level`, `description`)
VALUES
-- 1. 무빙 (시리즈)
('SERIES', '무빙', 'https://fastly.picsum.photos/id/216/400/225.jpg?hmac=vDVExR-xAUKG9oTxAy3DbuV-d7mNKierFnURcdMhDs4', 'ACTIVE', 1, 'UPLUS',
  '{
    "description": "초능력을 숨긴 채 현재를 살아가는 아이들과 아픈 비밀을 감춘 채 과거를 살아온 부모들이 시대와 세대를 넘어 닥치는 거대한 위험에 함께 맞서는 휴먼 액션 시리즈",
    "release": "2023-08-09",
    "director": "박인제",
    "actors": "류승룡, 한효주, 조인성, 차태현"
  }'
),
-- 2. 엘리멘탈 (단편/영화)
('SINGLE', '엘리멘탈', 'https://fastly.picsum.photos/id/607/400/225.jpg?hmac=AswAKZwnBOUbUwACjFGjFFp-imy7TVI2OyLSVxPnm1o', 'ACTIVE', 1, 'BASIC',
  '{
    "description": "불, 물, 공기, 흙 4개의 원소들이 살고 있는 엘리멘트 시티. 재치 있고 열정 넘치는 앰버가 유쾌한 웨이드를 만나 특별한 우정을 쌓는 이야기",
    "release": "2023-06-14",
    "director": "피터 손",
    "actors": "레아 루이스, 마무두 아티"
  }'
),
-- 3. 사용자 영상 (단편)
('SINGLE', '무빙후기쇼츠', 'https://fastly.picsum.photos/id/216/400/225.jpg?hmac=vDVExR-xAUKG9oTxAy3DbuV-d7mNKierFnURcdMhDs4', 'ACTIVE', 1, 'FREE',
  '{
    "description": "불, 물, 공기, 흙 4개의 원소들이 살고 있는 엘리멘트 시티. 재치 있고 열정 넘치는 앰버가 유쾌한 웨이드를 만나 특별한 우정을 쌓는 이야기"
  }'
);

-- 비디오 12건
INSERT INTO videos (content_id, episode_no, title, description, thumbnail_url, view_count, status, created_at, updated_at
) VALUES
(6, 1, '무빙 1화', '무빙 1화 설명', 'https://example.com/thumb/1_1.jpg', 1250, 'PUBLIC', NOW(), NOW()),
(6, 2, '무빙 2화', '무빙 2화 설명', 'https://example.com/thumb/1_2.jpg', 2080, 'PUBLIC', NOW(), NOW()),
(6, 3, '무빙 3화', '무빙 3화 설명', 'https://example.com/thumb/1_3.jpg', 200, 'PUBLIC', NOW(), NOW()),
(6, 4, '무빙 4화', '무빙 4화 설명', 'https://example.com/thumb/1_4.jpg', 10, 'DRAFT', NOW(), NOW()),
(6, 5, '무빙 5화', '무빙 5화 설명', 'https://example.com/thumb/1_5.jpg', 1250, 'PUBLIC', NOW(), NOW()),
(6, 6, '무빙 6화', '무빙 6화 설명', 'https://example.com/thumb/1_6.jpg', 2080, 'PUBLIC', NOW(), NOW()),
(6, 7, '무빙 7화', '무빙 7화 설명', 'https://example.com/thumb/1_7.jpg', 200, 'PUBLIC', NOW(), NOW()),
(6, 8, '무빙 8화', '무빙 8화 설명', 'https://example.com/thumb/1_8.jpg', 10, 'DRAFT', NOW(), NOW()),
(6, 9, '무빙 9화', '무빙 9화 설명', 'https://example.com/thumb/1_9.jpg', 1250, 'PUBLIC', NOW(), NOW()),
(6, 10, '무빙 10화', '무빙 10화 설명', 'https://example.com/thumb/1_10.jpg', 2080, 'PUBLIC', NOW(), NOW()),
(7, 1, '엘리멘탈 비디오 제목', null, null, 200, 'PUBLIC', NOW(), NOW()),
(8, 1, '사용자 비디오 제목', null, null, 10, 'DRAFT', NOW(), NOW())
;

-- 비디오파일 12건
INSERT INTO video_files (video_id, original_url, hls_url, duration_sec, transcode_status)
VALUES
(1, NULL, 'https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8', 634, 'DONE'),
(2, NULL, 'https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8', 634, 'DONE'),
(3, NULL, 'https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8', 634, 'DONE'),
(4, NULL, 'https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8', 634, 'DONE'),
(5, NULL, 'https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8', 634, 'DONE'),
(6, NULL, 'https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8', 634, 'DONE'),
(7, NULL, 'https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8', 634, 'DONE'),
(8, NULL, 'https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8', 634, 'DONE'),
(9, NULL, 'https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8', 634, 'DONE'),
(10, NULL, 'https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8', 634, 'DONE'),
(11, NULL, 'https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8', 634, 'DONE'),
(12, NULL, 'https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8', 634, 'DONE')
;

-- 시청이력 3건
-- 모두 1번 사용자가 시청했다고 가정
INSERT INTO watch_histories
(user_id, content_id, video_id, status, last_position_sec, last_watched_at, completed_at, created_at, updated_at)
VALUES
-- 1. [완독] 1번 영상 (시리즈물 6번의 1화 가정) : 634초 중 630초 시청 -> COMPLETED
-- 상황: 그저께 처음 틀었고(created -2일), 어제 다 보고 껐음(last_watched -1일)
(1, 6, 1, 'COMPLETED', 630,
 DATE_SUB(NOW(), INTERVAL 1 DAY), -- last_watched_at: 어제
 DATE_SUB(NOW(), INTERVAL 1 DAY), -- completed_at: 어제 다 봄
 DATE_SUB(NOW(), INTERVAL 2 DAY), -- created_at: 그저께 처음 진입
 DATE_SUB(NOW(), INTERVAL 1 DAY)  -- updated_at: 어제 마지막 갱신
),

-- 2. [시청중] 2번 영상 (시리즈물 6번의 2화 가정) : 634초 중 120초 시청 -> WATCHING
-- 상황: 오늘 3시간 전에 처음 틀었고(created -3시간), 120초 보다가 끔(last_watched -2시간)
(1, 6, 2, 'WATCHING', 120,
 DATE_SUB(NOW(), INTERVAL 2 HOUR),             -- last_watched_at: 2시간 전
 NULL,                                         -- completed_at: 아직 다 안 봄
 DATE_SUB(NOW(), INTERVAL '2:02' HOUR_MINUTE), -- created_at: 2시간 2분 전에 시작함
 DATE_SUB(NOW(), INTERVAL 2 HOUR)              -- updated_at: 2시간 전 마지막 갱신
),

-- 3. [초반진입] 12번 영상 (단건 영상. 콘텐츠id8 = 비디오id12 가정) : 634초 중 15초 시청 -> STARTED
-- 상황: 방금 클릭해서 데이터가 생성되었으나, 60초 미만이라 위치 저장은 안 됨(0초)
(1, 8, 12, 'STARTED', 0,
 NOW(), -- last_watched_at: 방금
 NULL,  -- completed_at: NULL
 NOW(), -- created_at: 방금
 NOW()  -- updated_at: 방금
);