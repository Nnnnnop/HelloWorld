-- Member resource tier for MEMBER role (L1 = baseline library access, L2 = + Level 2 visibility & portal privileges).

ALTER TABLE user_account
    ADD COLUMN member_site_tier VARCHAR(10) NOT NULL DEFAULT 'L2';

-- Guests do not inherit L2 until promoted; column only meaningful after Member.
UPDATE user_account SET member_site_tier = 'L1' WHERE role = 'GUEST';

ALTER TABLE join_application
    ADD COLUMN approved_member_site_tier VARCHAR(10) NULL;
