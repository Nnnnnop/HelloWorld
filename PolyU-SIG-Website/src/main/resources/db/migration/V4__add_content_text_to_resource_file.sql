ALTER TABLE resource_file
ADD COLUMN IF NOT EXISTS content_text TEXT;

UPDATE resource_file
SET content_text = ''
WHERE content_text IS NULL;
