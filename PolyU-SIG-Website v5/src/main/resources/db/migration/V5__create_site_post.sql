CREATE TABLE IF NOT EXISTS site_post (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    summary TEXT NOT NULL DEFAULT '',
    type VARCHAR(20) NOT NULL,
    published BOOLEAN NOT NULL DEFAULT TRUE,
    pinned BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    author_id BIGINT NOT NULL REFERENCES user_account(id)
);

CREATE INDEX IF NOT EXISTS idx_site_post_type_published_created_at
    ON site_post(type, published, created_at DESC);
