ALTER TABLE site_post
    ADD COLUMN IF NOT EXISTS news_date DATE,
    ADD COLUMN IF NOT EXISTS news_image_ids TEXT;
