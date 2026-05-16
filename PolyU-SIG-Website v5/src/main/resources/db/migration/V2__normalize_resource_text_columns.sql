DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'resource_file'
          AND column_name = 'description'
          AND data_type = 'bytea'
    ) THEN
        ALTER TABLE resource_file
            ALTER COLUMN description TYPE text
            USING convert_from(description, 'UTF8');
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'resource_file'
          AND column_name = 'tags'
          AND data_type = 'bytea'
    ) THEN
        ALTER TABLE resource_file
            ALTER COLUMN tags TYPE text
            USING convert_from(tags, 'UTF8');
    END IF;
END $$;
