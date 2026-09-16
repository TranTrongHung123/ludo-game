CREATE TABLE match_players (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    match_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    color VARCHAR(10) NOT NULL,
    `rank` TINYINT UNSIGNED NULL,
    score_earned DECIMAL(10, 1) NOT NULL DEFAULT 0.0,
    result VARCHAR(20) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_match_players_match
        FOREIGN KEY (match_id) REFERENCES matches (id),
    CONSTRAINT fk_match_players_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_match_players_user UNIQUE (match_id, user_id),
    CONSTRAINT uk_match_players_color UNIQUE (match_id, color),
    CONSTRAINT uk_match_players_rank UNIQUE (match_id, `rank`),
    CONSTRAINT chk_match_players_rank CHECK (`rank` IS NULL OR `rank` BETWEEN 1 AND 4),
    CONSTRAINT chk_match_players_score_non_negative CHECK (score_earned >= 0),
    INDEX idx_match_players_user (user_id)
) ENGINE = InnoDB;
