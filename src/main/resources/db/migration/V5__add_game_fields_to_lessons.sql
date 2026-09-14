ALTER TABLE lessons
    ADD COLUMN game_type VARCHAR(50),
    ADD COLUMN game_prompt TEXT,
    ADD COLUMN game_options TEXT,
    ADD COLUMN game_answer TEXT,
    ADD COLUMN success_message TEXT,
    ADD COLUMN retry_message TEXT;
