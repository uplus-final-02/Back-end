-- ============================================================
-- 스케줄러 워터마크 공유 테이블
--
-- [목적]
-- ContentRealtimeSyncScheduler의 lastSyncedAt 워터마크를
-- Redis + MySQL 이중 저장해 두 인스턴스 간 일관성 유지.
--
-- [폴백 우선순위]
--   1. Redis  : 빠른 읽기/쓰기 (정상 경로)
--   2. MySQL  : Redis 다운 시 폴백 — 이 테이블에서 읽음
--   3. 인메모리: MySQL도 다운 시 마지막 폴백 (duplicates 가능하지만 데이터 손상 없음)
--
-- [Redis 다운 + 인스턴스 교대 시나리오]
--   #1이 T=0에 실행 → MySQL에 watermark=T0 저장
--   #2가 T=30에 락 획득 → Redis 없음 → MySQL에서 T0 읽음 → 중복 없음
--
-- [컬럼]
--   scheduler_name : 스케줄러 식별자 (PK)
--   watermark      : 마지막으로 처리 완료된 시각
--   updated_at     : 워터마크 갱신 시각
-- ============================================================
CREATE TABLE IF NOT EXISTS scheduler_watermark
(
    scheduler_name VARCHAR(64)  NOT NULL,
    watermark      DATETIME(3)  NOT NULL,
    updated_at     DATETIME(3)  NOT NULL DEFAULT NOW(3),
    PRIMARY KEY (scheduler_name)
);
