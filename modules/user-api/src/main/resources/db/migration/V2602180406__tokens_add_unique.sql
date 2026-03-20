-- V2602180406__tokens_add_unique.sql
-- 목적: 유저당 refresh 1개(회전/표준 로그아웃)

ALTER TABLE tokens_management
  ADD UNIQUE KEY UQ_tokens_management_user_id (user_id);
