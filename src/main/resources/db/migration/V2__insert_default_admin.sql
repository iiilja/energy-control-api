-- V2: Insert default admin user
-- Creates a default admin user with username 'admin' and password 'admin'
-- ⚠️ WARNING: Change this password immediately after first login!

-- Insert admin user
-- Password hash is BCrypt (strength 12) for "admin"
INSERT INTO users (username, password_hash, email, enabled, account_non_expired, account_non_locked, credentials_non_expired, created_at, updated_at)
VALUES (
    'admin',
    '$2a$12$8do/vPU.So6vfmmVYOwTmOyVfivHsjVv/j5zzBqqJjK51luBrS1ZK',  -- password: "admin"
    'admin@energy-control.local',
    true,
    true,
    true,
    true,
    NOW(),
    NOW()
);

-- Insert ROLE_ADMIN for admin user
INSERT INTO user_roles (user_id, role, created_at)
VALUES (
    (SELECT id FROM users WHERE username = 'admin'),
    'ROLE_ADMIN',
    NOW()
);

-- Insert ROLE_USER for admin user (so admin can access user endpoints too)
INSERT INTO user_roles (user_id, role, created_at)
VALUES (
    (SELECT id FROM users WHERE username = 'admin'),
    'ROLE_USER',
    NOW()
);

-- Log user creation
DO $$
BEGIN
    RAISE NOTICE '⚠️  DEFAULT ADMIN USER CREATED';
    RAISE NOTICE '   Username: admin';
    RAISE NOTICE '   Password: admin';
    RAISE NOTICE '   ⚠️  CHANGE THIS PASSWORD IMMEDIATELY!';
END $$;
