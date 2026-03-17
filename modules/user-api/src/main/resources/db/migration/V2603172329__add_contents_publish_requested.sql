ALTER TABLE contents
    ADD COLUMN publish_requested TINYINT(1) NOT NULL DEFAULT 0 AFTER access_level,
    ADD COLUMN publish_desired_status VARCHAR(20) NOT NULL DEFAULT 'HIDDEN' AFTER publish_requested;

CREATE INDEX idx_contents_publish_requested ON contents(publish_requested);