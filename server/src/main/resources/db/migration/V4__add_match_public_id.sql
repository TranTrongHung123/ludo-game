ALTER TABLE matches
    ADD COLUMN public_id VARCHAR(36) NULL AFTER id;

UPDATE matches
SET public_id = UUID()
WHERE public_id IS NULL;

ALTER TABLE matches
    MODIFY COLUMN public_id VARCHAR(36) NOT NULL,
    ADD CONSTRAINT uk_matches_public_id UNIQUE (public_id);
