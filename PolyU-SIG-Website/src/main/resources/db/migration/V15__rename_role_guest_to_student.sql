-- Rename site role GUEST -> STUDENT (enum persisted as string in user_account.role).
UPDATE user_account SET role = 'STUDENT' WHERE role = 'GUEST';
