-- ============================================================
-- ShedLock 분산 락 테이블
-- user-api 2개 인스턴스 환경에서 스케줄러 중복 실행 방지용.
--
-- 동작 원리:
--   1. 인스턴스 #1, #2 가 동시에 스케줄러 실행 시도
--   2. 두 인스턴스 모두 아래 패턴으로 락 획득 경쟁:
--        INSERT ... ON DUPLICATE KEY UPDATE ... WHERE lock_until <= NOW()
--   3. MySQL 이 원자적으로 처리 → 딱 1개 인스턴스만 성공
--   4. 락 획득 실패한 인스턴스는 해당 사이클 조용히 스킵
--
-- 컬럼 설명:
--   name       : 작업명 (PK). @SchedulerLock(name = "...")와 1:1 대응
--   lock_until : 락 만료 시각 (lockAtMostFor). 앱 크래시 시 데드락 방지 안전망
--   locked_at  : 락 획득 시각 (디버깅용)
--   locked_by  : 락 보유 인스턴스 (hostname:port 형태, 디버깅용)
-- ============================================================
CREATE TABLE IF NOT EXISTS shedlock
(
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP(3) NOT NULL,
    locked_at  TIMESTAMP(3) NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);
