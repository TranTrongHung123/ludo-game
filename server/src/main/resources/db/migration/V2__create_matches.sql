CREATE TABLE matches (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    started_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ended_at TIMESTAMP(6) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PLAYING',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_matches_started_at (started_at),
    INDEX idx_matches_status (status),
    CONSTRAINT chk_matches_time_order CHECK (ended_at IS NULL OR ended_at >= started_at)
) ENGINE = InnoDB;
