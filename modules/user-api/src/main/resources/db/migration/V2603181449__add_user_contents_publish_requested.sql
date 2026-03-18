ALTER TABLE user_contents
    ADD COLUMN publish_requested TINYINT(1) NOT NULL DEFAULT 0
        COMMENT 'publish 요청 여부(기본 0=false)'
        AFTER access_level;

CREATE INDEX idx_user_contents_publish_requested
    ON user_contents(publish_requested);