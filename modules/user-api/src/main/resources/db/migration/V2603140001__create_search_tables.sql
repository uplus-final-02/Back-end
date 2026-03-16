CREATE TABLE IF NOT EXISTS search_log (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    keyword       VARCHAR(200) NOT NULL,
    result_count  INT          NOT NULL,
    user_id       BIGINT,
    searched_at   DATETIME(6)  NOT NULL,
    INDEX idx_search_log_keyword    (keyword),
    INDEX idx_search_log_searched_at (searched_at),
    INDEX idx_search_log_result_count (result_count)
);

CREATE TABLE IF NOT EXISTS es_sync_failure (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    content_id   BIGINT       NOT NULL,
    failed_at    DATETIME(6)  NOT NULL,
    retry_count  INT          NOT NULL DEFAULT 0,
    last_error   VARCHAR(500),
    resolved     TINYINT(1)   NOT NULL DEFAULT 0,
    INDEX idx_es_sync_failure_content_id  (content_id),
    INDEX idx_es_sync_failure_retry_count (retry_count)
);