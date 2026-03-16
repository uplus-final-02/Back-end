-- 워터마크 커서를 updatedAt + id 복합 조건으로 변경
-- 동일 updatedAt 데이터가 여러 건일 때 페이지 경계 불안정 + 마지막 배치 반복 읽기 방지
ALTER TABLE scheduler_watermark
    ADD COLUMN last_id BIGINT NOT NULL DEFAULT 0 AFTER watermark;