-- admin user가 없으면 생성
INSERT INTO users (nickname, profile_image, user_role, user_status, created_at, updated_at)
SELECT 'admin', NULL, 'ADMIN', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM users WHERE nickname='admin' AND user_role='ADMIN'
);

-- admin user_id 확보
SET @admin_user_id := (
  SELECT user_id FROM users WHERE nickname='admin' AND user_role='ADMIN' LIMIT 1
);

-- admin auth_account가 없으면 생성
INSERT INTO auth_accounts (user_id, auth_provider, auth_provider_subject, email, password_hash, created_at, last_login_at)
SELECT @admin_user_id, 'EMAIL', 'admin@admin.com', 'admin@admin.com',
       '$2a$10$I.MLXBOlJ2G4SckoFzI7xuLw8Leif4Xr/bwK23WkGvyc8e4cWca.2',
       NOW(), NULL
WHERE NOT EXISTS (
  SELECT 1 FROM auth_accounts WHERE auth_provider='EMAIL' AND email='admin@admin.com'
);