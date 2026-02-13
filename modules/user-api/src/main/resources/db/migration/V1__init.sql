-- V1__init_schema.sql
-- MySQL 8.x / InnoDB / utf8mb4
-- Tables + PK/FK/NN/default only (indexes/UK are added later except those already defined)

-- NOTE:
-- 1) Flyway는 "DB 자체 생성(CREATE DATABASE)"을 기본으로 책임지기 어렵습니다.
--    이 스크립트는 "sendapp" DB에 연결된 상태에서 실행된다고 가정합니다.
-- 2) billing_history_id는 ERD 의도대로 "논리 FK(polymorphic)" 이므로 물리 FK를 걸지 않습니다.
-- 3) FK 삭제 연쇄를 피하기 위해 기본은 RESTRICT/NO ACTION을 사용합니다.

-- =========================
-- Master tables
-- =========================

