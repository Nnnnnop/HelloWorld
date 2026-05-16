DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'resource_file'
          AND column_name = 'title'
          AND data_type = 'bytea'
    ) THEN
        ALTER TABLE resource_file
            ALTER COLUMN title TYPE varchar(255)
            USING convert_from(title, 'UTF8');
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'resource_file'
          AND column_name = 'file_name'
          AND data_type = 'bytea'
    ) THEN
        ALTER TABLE resource_file
            ALTER COLUMN file_name TYPE varchar(255)
            USING convert_from(file_name, 'UTF8');
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'resource_file'
          AND column_name = 'file_type'
          AND data_type = 'bytea'
    ) THEN
        ALTER TABLE resource_file
            ALTER COLUMN file_type TYPE varchar(255)
            USING convert_from(file_type, 'UTF8');
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'resource_file'
          AND column_name = 'category'
          AND data_type = 'bytea'
    ) THEN
        ALTER TABLE resource_file
            ALTER COLUMN category TYPE varchar(100)
            USING convert_from(category, 'UTF8');
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'user_account'
          AND column_name = 'username'
          AND data_type = 'bytea'
    ) THEN
        ALTER TABLE user_account
            ALTER COLUMN username TYPE varchar(40)
            USING convert_from(username, 'UTF8');
    END IF;
END $$;
