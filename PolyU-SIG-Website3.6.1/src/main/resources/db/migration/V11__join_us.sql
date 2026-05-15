CREATE TABLE IF NOT EXISTS interest_group (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    description TEXT,
    recruiting BOOLEAN NOT NULL DEFAULT TRUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS join_application (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES user_account (id),
    interest_group_id BIGINT NOT NULL REFERENCES interest_group (id),
    message TEXT,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    reviewed_at TIMESTAMP,
    reviewed_by VARCHAR(255)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_join_application_pending_user_group
    ON join_application (user_id, interest_group_id)
    WHERE status = 'PENDING';

CREATE TABLE IF NOT EXISTS user_group_membership (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES user_account (id),
    interest_group_id BIGINT NOT NULL REFERENCES interest_group (id),
    created_at TIMESTAMP NOT NULL,
    UNIQUE (user_id, interest_group_id)
);

INSERT INTO interest_group (name, description, recruiting, active, sort_order, created_at)
VALUES
    ('Algorithm & Competitive Programming',
     'Practice contests, training sessions, and interview preparation with peers.',
     TRUE, TRUE, 10, CURRENT_TIMESTAMP),
    ('Embedded & IoT Lab',
     'Hands-on hardware, sensors, and project-based learning.',
     TRUE, TRUE, 20, CURRENT_TIMESTAMP),
    ('Open Source & Collaboration',
     'Contribute to community projects and peer code reviews.',
     TRUE, TRUE, 30, CURRENT_TIMESTAMP);
