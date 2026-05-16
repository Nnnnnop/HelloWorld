CREATE TABLE IF NOT EXISTS user_resource_favourite (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    resource_file_id BIGINT NOT NULL REFERENCES resource_file(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_user_resource_favourite UNIQUE (user_id, resource_file_id)
);

CREATE INDEX IF NOT EXISTS idx_user_resource_favourite_user_id ON user_resource_favourite(user_id);
