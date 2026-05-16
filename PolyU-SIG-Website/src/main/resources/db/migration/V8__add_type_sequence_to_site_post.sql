ALTER TABLE site_post
    ADD COLUMN IF NOT EXISTS type_sequence INTEGER;

WITH ranked AS (
    SELECT id,
           ROW_NUMBER() OVER (PARTITION BY type ORDER BY created_at, id) AS seq
    FROM site_post
)
UPDATE site_post p
SET type_sequence = ranked.seq
FROM ranked
WHERE p.id = ranked.id
  AND p.type_sequence IS NULL;

UPDATE site_post
SET type_sequence = 1
WHERE type_sequence IS NULL;

ALTER TABLE site_post
    ALTER COLUMN type_sequence SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_site_post_type_sequence
    ON site_post(type, type_sequence);
