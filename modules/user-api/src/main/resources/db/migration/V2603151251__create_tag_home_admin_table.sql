CREATE TABLE tag_home_stats (
    tag_home_stats_id BIGINT NOT NULL AUTO_INCREMENT,
    stat_date DATE NOT NULL COMMENT '통계 기준 일자',
    tag_id BIGINT NOT NULL COMMENT 'tags.tag_id',
    total_view_count BIGINT NOT NULL DEFAULT 0 COMMENT '태그에 연결된 콘텐츠들의 총 조회수 합',
    total_bookmark_count BIGINT NOT NULL DEFAULT 0 COMMENT '태그에 연결된 콘텐츠들의 총 북마크 수 합',
    bookmark_rate DECIMAL(10,4) DEFAULT NULL COMMENT '총 북마크 수 / 총 조회수',
    completed_view_count BIGINT DEFAULT NULL COMMENT '완료 시청 수 합(원천 데이터 확보 전까지 NULL)',
    completion_rate DECIMAL(10,4) DEFAULT NULL COMMENT '완료율(원천 데이터 확보 전까지 NULL)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (tag_home_stats_id),
    UNIQUE KEY uk_tag_home_stats_stat_date_tag_id (stat_date, tag_id),
    KEY idx_tag_home_stats_tag_id (tag_id),
    CONSTRAINT fk_tag_home_stats_tag
        FOREIGN KEY (tag_id) REFERENCES tags(tag_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='홈 노출 태그 일별 통계';