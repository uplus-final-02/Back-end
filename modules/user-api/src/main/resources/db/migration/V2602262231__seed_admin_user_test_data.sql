-- ============================================================
-- Admin User List/Detail 테스트 시드 데이터 (user_id: 101~110)
-- - users + auth_accounts(다양한 가입 방식 + 일부 멀티 로그인)
-- - subscriptions(일부만, 상태 다양)
-- - payments(결제수단 다양, request_at desc 검증 가능)
-- ※ 스키마 변경(ALTER TABLE) 절대 포함하지 않음 (데이터 전용)
-- ============================================================

-- (선택) 재실행 대비: FK 역순 삭제
DELETE FROM payments      WHERE user_id BETWEEN 101 AND 110;
DELETE FROM subscriptions WHERE user_id BETWEEN 101 AND 110;
DELETE FROM auth_accounts WHERE user_id BETWEEN 101 AND 110;
DELETE FROM users         WHERE user_id BETWEEN 101 AND 110;

-- ------------------------------------------------------------
-- 1) USERS (101~110) : 가입일시 모두 다르게 (created_at desc 정렬 검증)
-- ------------------------------------------------------------
INSERT INTO users (user_id, nickname, profile_image, user_role, user_status, created_at, updated_at, deleted_at) VALUES
                                                                                                                     (101, 'u101_newest', NULL, 'USER', 'ACTIVE', '2026-02-25 18:10:00.000000', '2026-02-25 18:10:00.000000', NULL),
                                                                                                                     (102, 'u102_new',    NULL, 'USER', 'ACTIVE', '2026-02-24 10:30:00.000000', '2026-02-24 10:30:00.000000', NULL),
                                                                                                                     (103, 'u103_mid',    NULL, 'USER', 'ACTIVE', '2026-02-20 09:15:00.000000', '2026-02-20 09:15:00.000000', NULL),
                                                                                                                     (104, 'u104_old',    NULL, 'USER', 'ACTIVE', '2026-02-10 11:40:00.000000', '2026-02-10 11:40:00.000000', NULL),
                                                                                                                     (105, 'u105_older',  NULL, 'USER', 'ACTIVE', '2026-01-28 07:05:00.000000', '2026-01-28 07:05:00.000000', NULL),
                                                                                                                     (106, 'u106_jan',    NULL, 'USER', 'ACTIVE', '2026-01-15 13:20:00.000000', '2026-01-15 13:20:00.000000', NULL),
                                                                                                                     (107, 'u107_dec',    NULL, 'USER', 'ACTIVE', '2025-12-20 22:10:00.000000', '2025-12-20 22:10:00.000000', NULL),
                                                                                                                     (108, 'u108_nov',    NULL, 'USER', 'ACTIVE', '2025-11-05 08:50:00.000000', '2025-11-05 08:50:00.000000', NULL),
                                                                                                                     (109, 'u109_oct',    NULL, 'USER', 'ACTIVE', '2025-10-12 16:00:00.000000', '2025-10-12 16:00:00.000000', NULL),
                                                                                                                     (110, 'u110_sep',    NULL, 'USER', 'ACTIVE', '2025-09-01 09:00:00.000000', '2025-09-01 09:00:00.000000', NULL);

-- ------------------------------------------------------------
-- 2) AUTH_ACCOUNTS : 가입 방식 다양 + 일부 멀티 로그인
--  - 유니크:
--    * (auth_provider, auth_provider_subject)
--    * (user_id, auth_provider)
--  - 따라서 멀티 로그인은 "다른 provider"로 추가해야 함
-- ------------------------------------------------------------
INSERT INTO auth_accounts (
    auth_account_id, user_id, auth_provider, auth_provider_subject, email, password_hash, created_at, last_login_at
) VALUES
      (1001, 101, 'EMAIL',  'email:101',        'u101_newest@example.com', NULL, '2026-02-25 18:10:05.000000', '2026-02-26 10:00:00.000000'),
      (1002, 102, 'GOOGLE', 'google:102',       'u102_new@gmail.com',      NULL, '2026-02-24 10:30:10.000000', '2026-02-25 09:00:00.000000'),
      (1003, 103, 'KAKAO',  'kakao:103',        NULL,                     NULL, '2026-02-20 09:15:20.000000', '2026-02-23 21:00:00.000000'),
      (1004, 104, 'NAVER',  'naver:104',        'u104_old@naver.com',     NULL, '2026-02-10 11:40:10.000000', '2026-02-11 11:00:00.000000'),
      (1005, 105, 'EMAIL',  'email:105',        'u105_older@example.com', NULL, '2026-01-28 07:05:10.000000', '2026-02-01 08:00:00.000000'),
      (1006, 106, 'GOOGLE', 'google:106',       'u106_jan@gmail.com',     NULL, '2026-01-15 13:20:10.000000', '2026-02-10 10:00:00.000000'),
      (1007, 107, 'EMAIL',  'email:107',        'u107_dec@example.com',   NULL, '2025-12-20 22:10:10.000000', '2026-01-05 12:00:00.000000'),
      (1008, 108, 'KAKAO',  'kakao:108',        NULL,                     NULL, '2025-11-05 08:50:10.000000', '2026-02-12 20:00:00.000000'),
      (1009, 109, 'NAVER',  'naver:109',        NULL,                     NULL, '2025-10-12 16:00:10.000000', '2025-12-01 09:00:00.000000'),
      (1010, 110, 'EMAIL',  'email:110',        'u110_sep@example.com',   NULL, '2025-09-01 09:00:10.000000', '2025-09-10 09:00:00.000000'),

-- 멀티 로그인 케이스: 103은 KAKAO + EMAIL
      (1011, 103, 'EMAIL',  'email:103',        'u103_mid@example.com',   NULL, '2026-02-20 09:16:00.000000', '2026-02-24 12:00:00.000000'),

-- 멀티 로그인 케이스: 106은 GOOGLE + KAKAO (subject 유니크 충돌 없게 별도 값)
      (1012, 106, 'KAKAO',  'kakao:106-alt',    NULL,                     NULL, '2026-01-15 13:21:00.000000', '2026-02-18 19:00:00.000000');

-- ------------------------------------------------------------
-- 3) SUBSCRIPTIONS : 일부 유저만 구독 보유 (상태 다양)
--  - subscription_id 고정해서 payments에서 FK로 사용
--  - expires_at 컬럼명 사용 (스키마 기준)
--  - created_at/updated_at은 NOT NULL일 수 있어 명시
-- ------------------------------------------------------------
INSERT INTO subscriptions (
    subscription_id, user_id, plan_type, subscription_status, started_at, expires_at, created_at, updated_at
) VALUES
      (2001, 101, 'SUB_BASIC', 'ACTIVE',   '2026-02-25 18:10:00.000000', '2026-03-25 18:10:00.000000', '2026-02-25 18:10:00.000000', '2026-02-25 18:10:00.000000'),
      (2002, 102, 'SUB_BASIC', 'ACTIVE',   '2026-02-24 10:30:00.000000', '2026-03-24 10:30:00.000000', '2026-02-24 10:30:00.000000', '2026-02-24 10:30:00.000000'),
      (2003, 104, 'SUB_BASIC', 'EXPIRED',  '2026-01-10 11:00:00.000000', '2026-02-10 11:00:00.000000', '2026-01-10 11:00:00.000000', '2026-02-10 11:00:00.000000'),
      (2004, 106, 'SUB_BASIC', 'CANCELED', '2026-01-15 13:20:00.000000', '2026-02-15 13:20:00.000000', '2026-01-15 13:20:00.000000', '2026-01-20 09:00:00.000000'),
      (2005, 108, 'SUB_BASIC', 'ACTIVE',   '2025-11-05 08:50:00.000000', '2026-03-05 08:50:00.000000', '2025-11-05 08:50:00.000000', '2026-02-12 20:00:00.000000');

-- 103/105/107/109/110 은 "구독 없음" 케이스

-- ------------------------------------------------------------
-- 4) PAYMENTS : 결제 이력 다양 (결제수단 다양)
--  - payment_provider: PaymentMethod(enum) = CARD, KAKAO_PAY, NAVER_PAY
--  - payment_status: PaymentStatus(enum) 값과 일치
--  - request_at desc 정렬 검증 위해 시간 다르게 구성
--  - created_at/updated_at은 NOT NULL일 수 있어 명시
-- ------------------------------------------------------------

-- user 101 (ACTIVE) : 3건
INSERT INTO payments (
    payment_id, subscription_id, user_id, amount, payment_status, payment_provider,
    request_at, approved_at, created_at, updated_at
) VALUES
      (3001, 2001, 101, 9900, 'SUCCEEDED', 'CARD',      '2026-02-25 18:10:00.000000', '2026-02-25 18:10:05.000000', '2026-02-25 18:10:00.000000', '2026-02-25 18:10:00.000000'),
      (3002, 2001, 101, 9900, 'FAILED',    'KAKAO_PAY', '2026-02-20 09:30:00.000000', NULL,                          '2026-02-20 09:30:00.000000', '2026-02-20 09:30:00.000000'),
      (3003, 2001, 101, 9900, 'SUCCEEDED', 'NAVER_PAY', '2026-02-10 11:00:00.000000', '2026-02-10 11:00:03.000000', '2026-02-10 11:00:00.000000', '2026-02-10 11:00:00.000000');

-- user 102 (ACTIVE) : 2건
INSERT INTO payments (
    payment_id, subscription_id, user_id, amount, payment_status, payment_provider,
    request_at, approved_at, created_at, updated_at
) VALUES
      (3004, 2002, 102, 9900, 'SUCCEEDED', 'KAKAO_PAY', '2026-02-24 10:30:00.000000', '2026-02-24 10:30:03.000000', '2026-02-24 10:30:00.000000', '2026-02-24 10:30:00.000000'),
      (3005, 2002, 102, 9900, 'SUCCEEDED', 'CARD',      '2026-02-23 08:00:00.000000', '2026-02-23 08:00:02.000000', '2026-02-23 08:00:00.000000', '2026-02-23 08:00:00.000000');

-- user 104 (EXPIRED) : 2건
INSERT INTO payments (
    payment_id, subscription_id, user_id, amount, payment_status, payment_provider,
    request_at, approved_at, created_at, updated_at
) VALUES
      (3006, 2003, 104, 9900, 'SUCCEEDED', 'NAVER_PAY', '2026-02-09 08:00:00.000000', '2026-02-09 08:00:03.000000', '2026-02-09 08:00:00.000000', '2026-02-09 08:00:00.000000'),
      (3007, 2003, 104, 9900, 'SUCCEEDED', 'CARD',      '2026-01-10 11:00:00.000000', '2026-01-10 11:00:03.000000', '2026-01-10 11:00:00.000000', '2026-01-10 11:00:00.000000');

-- user 106 (CANCELED) : 1건
INSERT INTO payments (
    payment_id, subscription_id, user_id, amount, payment_status, payment_provider,
    request_at, approved_at, created_at, updated_at
) VALUES
    (3008, 2004, 106, 9900, 'SUCCEEDED', 'CARD',      '2026-01-15 13:20:00.000000', '2026-01-15 13:20:03.000000', '2026-01-15 13:20:00.000000', '2026-01-15 13:20:00.000000');

-- user 108 (ACTIVE) : 2건
INSERT INTO payments (
    payment_id, subscription_id, user_id, amount, payment_status, payment_provider,
    request_at, approved_at, created_at, updated_at
) VALUES
      (3009, 2005, 108, 9900, 'SUCCEEDED', 'KAKAO_PAY', '2026-02-12 20:00:00.000000', '2026-02-12 20:00:03.000000', '2026-02-12 20:00:00.000000', '2026-02-12 20:00:00.000000'),
      (3010, 2005, 108, 9900, 'FAILED',    'NAVER_PAY', '2026-01-05 09:00:00.000000', NULL,                          '2026-01-05 09:00:00.000000', '2026-01-05 09:00:00.000000');

-- ------------------------------------------------------------
-- (선택) 검증용 쿼리
-- ------------------------------------------------------------
-- SELECT user_id, nickname, created_at FROM users WHERE user_id BETWEEN 101 AND 110 ORDER BY created_at DESC;
-- SELECT * FROM subscriptions WHERE user_id=101;
-- SELECT * FROM payments WHERE user_id=101 ORDER BY request_at DESC;