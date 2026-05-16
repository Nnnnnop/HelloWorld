CREATE TABLE IF NOT EXISTS folder (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    parent_id BIGINT REFERENCES folder(id)
);

-- Add folder_id to resource_file table
ALTER TABLE resource_file ADD COLUMN folder_id BIGINT REFERENCES folder(id);

-- Create a default root folder
INSERT INTO folder (name, parent_id) VALUES ('Root', NULL);

-- Update existing files to use the root folder
UPDATE resource_file SET folder_id = (SELECT id FROM folder WHERE name = 'Root' LIMIT 1) WHERE folder_id IS NULL;

-- Make folder_id NOT NULL after populating
ALTER TABLE resource_file ALTER COLUMN folder_id SET NOT NULL;