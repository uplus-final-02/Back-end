-- V2602251740__contents_idx.sql
-- trending_rank 테이블 추가
-- idx 추가

CREATE TABLE trending_history (
    history_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    content_id BIGINT NOT NULL,
    ranking INT NOT NULL,
    trending_score DOUBLE NOT NULL,
    delta_view_count BIGINT NOT NULL,
    delta_bookmark_count BIGINT NOT NULL,
    delta_completed_count BIGINT NOT NULL,
    calculated_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_trending_history_calculated_at_ranking
ON trending_history (calculated_at, ranking);