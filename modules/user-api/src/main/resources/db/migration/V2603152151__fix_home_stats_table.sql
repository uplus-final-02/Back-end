## 이름 변경
ALTER TABLE tag_home_stats
    RENAME COLUMN completed_view_count TO completed_watch_count;

## 새 컬럼 추가
ALTER TABLE tag_home_stats
    ADD COLUMN total_watch_count BIGINT NOT NULL DEFAULT 0 AFTER total_bookmark_count;
    
## 컬럼 위치 정리
ALTER TABLE tag_home_stats
    MODIFY COLUMN total_watch_count BIGINT NOT NULL DEFAULT 0 AFTER total_bookmark_count,
    MODIFY COLUMN completed_watch_count BIGINT AFTER total_watch_count,
    MODIFY COLUMN completion_rate DECIMAL(10,4) AFTER completed_watch_count;
    
