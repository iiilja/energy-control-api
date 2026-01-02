-- PostgreSQL initialization script
-- This file is executed when the container is first created
-- The actual schema is managed by Flyway migrations in the Spring Boot application

-- Set timezone to UTC
SET TIME ZONE 'UTC';

-- Enable extensions if needed
-- CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
-- CREATE EXTENSION IF NOT EXISTS "pg_trgm";

-- Grant privileges (optional, user is already the owner)
GRANT ALL PRIVILEGES ON DATABASE energy_control TO energy_user;

-- Log initialization
DO $$
BEGIN
    RAISE NOTICE 'PostgreSQL database initialized successfully for Energy Control API';
END $$;
