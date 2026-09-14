ALTER TABLE lessons
    RENAME COLUMN video_provider TO content_type;

ALTER TABLE lessons
    ALTER COLUMN video_ref DROP NOT NULL;

ALTER TABLE lessons
    ADD COLUMN content TEXT,
    ADD COLUMN instructions TEXT,
    ADD COLUMN questions TEXT,
    ADD COLUMN asset_url VARCHAR(500),
    ADD COLUMN download_url VARCHAR(500);
