-- Create upload session table
CREATE TABLE upload_session (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(255) NOT NULL UNIQUE,
    uploader VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'IN_PROGRESS',
    total_files BIGINT NOT NULL DEFAULT 0,
    uploaded_files BIGINT NOT NULL DEFAULT 0,
    failed_files BIGINT NOT NULL DEFAULT 0,
    total_bytes BIGINT NOT NULL DEFAULT 0,
    uploaded_bytes BIGINT NOT NULL DEFAULT 0,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_session_id ON upload_session(session_id);
CREATE INDEX IF NOT EXISTS idx_uploader_status ON upload_session(uploader, status);
CREATE INDEX IF NOT EXISTS idx_expires_at ON upload_session(expires_at);

-- Create upload task record table
CREATE TABLE upload_task_record (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL,
    client_path VARCHAR(1024) NOT NULL,
    display_name VARCHAR(1024) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'QUEUED',
    file_size BIGINT NOT NULL DEFAULT 0,
    uploaded_bytes BIGINT NOT NULL DEFAULT 0,
    file_hash VARCHAR(64),
    resource_file_id BIGINT,
    folder_id BIGINT NOT NULL,
    category VARCHAR(255),
    description TEXT,
    tags TEXT,
    visibility VARCHAR(50),
    retry_count INT DEFAULT 0,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_upload_task_session FOREIGN KEY (session_id) REFERENCES upload_session(id) ON DELETE CASCADE,
    CONSTRAINT fk_upload_task_folder FOREIGN KEY (folder_id) REFERENCES folder(id) ON DELETE CASCADE,
    CONSTRAINT fk_upload_task_resource FOREIGN KEY (resource_file_id) REFERENCES resource_file(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_session_status ON upload_task_record(session_id, status);
CREATE INDEX IF NOT EXISTS idx_file_hash ON upload_task_record(file_hash);
CREATE INDEX IF NOT EXISTS idx_status_retry ON upload_task_record(status, retry_count);
