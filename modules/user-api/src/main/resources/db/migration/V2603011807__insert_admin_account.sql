-- 1) admin user 있는지 확인
SELECT id, nickname, user_role FROM users WHERE nickname = 'admin';

-- 2) 그 user_id로 auth_accounts 남은 것 있는지 확인
SELECT * FROM auth_accounts WHERE email = 'admin@admin.com' OR auth_provider_subject = 'admin@admin.com';

INSERT INTO users (
    nickname,
    profile_image,
    user_role,
    user_status,
    created_at,
    updated_at
)
VALUES (
    'admin',
    NULL,
    'ADMIN',
    'ACTIVE',
    NOW(),
    NOW()
);

SET @admin_user_id = LAST_INSERT_ID();

INSERT INTO auth_accounts (
    user_id,
    auth_provider,
    auth_provider_subject,
    email,
    password_hash,
    created_at,
    last_login_at
)
VALUES (
    @admin_user_id,
    'EMAIL',
    'admin@admin.com', 
    'admin@admin.com',
    '$2y$05$zh9dQShRUSnEAxvTCLAFnOmXkGpcpS/5A9gZ3oKQ/scietSzyxYzu',
    NOW(),
    NULL
);