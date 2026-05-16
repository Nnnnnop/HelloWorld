ALTER TABLE site_post
    ADD COLUMN IF NOT EXISTS event_start_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS event_end_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS organizer VARCHAR(255),
    ADD COLUMN IF NOT EXISTS event_time_label VARCHAR(255),
    ADD COLUMN IF NOT EXISTS venue VARCHAR(255),
    ADD COLUMN IF NOT EXISTS event_category VARCHAR(80);

CREATE INDEX IF NOT EXISTS idx_site_post_event_start_at
    ON site_post(event_start_at);
