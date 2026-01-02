-- V1: Initial schema for Energy Control API
-- Creates tables for heat pump readings, system configuration, and user management

-- ===========================================================================
-- Table: heat_pump_readings
-- Primary table for storing all ESP32 heat pump data readings
-- ===========================================================================
CREATE TABLE heat_pump_readings (
    id BIGSERIAL PRIMARY KEY,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    collection_timestamp TIMESTAMP WITH TIME ZONE NOT NULL,

    -- Status fields (from ESP32 status object)
    connected BOOLEAN,
    last_update BIGINT,
    operation_mode SMALLINT,
    operation_mode_text VARCHAR(20),
    alarm_active BOOLEAN,
    compressor_running BOOLEAN,
    current_demand SMALLINT,
    current_demand_text VARCHAR(50),

    -- Temperature fields (from ESP32 temperatures object)
    outdoor_temp DECIMAL(5,2),
    brine_in_temp DECIMAL(5,2),
    brine_out_temp DECIMAL(5,2),
    system_supply_in_temp DECIMAL(5,2),
    system_supply_out_temp DECIMAL(5,2),
    system_supply_line_temp DECIMAL(5,2),
    system_supply_setpoint DECIMAL(5,2),
    tap_water_top_temp DECIMAL(5,2),
    tap_water_lower_temp DECIMAL(5,2),

    -- Compressor fields (from ESP32 compressor object)
    compressor_rpm INTEGER,
    compressor_speed INTEGER,
    compressor_hours INTEGER,

    -- Heating fields (from ESP32 heating object)
    heating_setpoint DECIMAL(4,2),
    heating_hours INTEGER,
    external_heater_hours INTEGER,

    -- Heat curve fields (from ESP32 heatCurve object)
    heat_curve_outdoor_temps DECIMAL(5,2)[],
    heat_curve_supply_temps DECIMAL(5,2)[],

    -- Pump fields (from ESP32 pump object)
    pump_auto_mode BOOLEAN,
    pump_current_state BOOLEAN,
    pump_manual_state BOOLEAN,
    pump_on_duration INTEGER,
    pump_off_duration INTEGER,
    pump_last_state_change BIGINT,
    pump_remaining_minutes INTEGER,

    -- Raw JSON for flexibility and debugging
    raw_json JSONB,

    CONSTRAINT heat_pump_readings_timestamp_unique UNIQUE (collection_timestamp)
);

-- Indexes for common query patterns
CREATE INDEX idx_readings_timestamp ON heat_pump_readings(collection_timestamp DESC);
CREATE INDEX idx_readings_date ON heat_pump_readings(collection_timestamp);
CREATE INDEX idx_readings_operation_mode ON heat_pump_readings(operation_mode);
CREATE INDEX idx_readings_compressor_running ON heat_pump_readings(compressor_running);
CREATE INDEX idx_readings_outdoor_temp ON heat_pump_readings(outdoor_temp);
CREATE INDEX idx_readings_raw_json ON heat_pump_readings USING GIN(raw_json);

-- ===========================================================================
-- Table: system_configuration
-- Key-value store for runtime configuration
-- ===========================================================================
CREATE TABLE system_configuration (
    id BIGSERIAL PRIMARY KEY,
    config_key VARCHAR(100) NOT NULL UNIQUE,
    config_value TEXT NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT valid_config_key CHECK (config_key ~ '^[a-z_][a-z0-9_.]*$')
);

CREATE INDEX idx_config_key ON system_configuration(config_key);

-- Initial configuration values
INSERT INTO system_configuration (config_key, config_value, description) VALUES
    ('esp32.base_url', 'http://192.168.8.125', 'ESP32 device base URL for API calls'),
    ('collection.interval_seconds', '60', 'Data collection interval in seconds'),
    ('collection.enabled', 'true', 'Enable or disable automatic data collection'),
    ('collection.timeout_seconds', '10', 'HTTP request timeout for ESP32 API calls in seconds');

-- ===========================================================================
-- Table: users
-- User authentication table
-- ===========================================================================
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    email VARCHAR(100),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    account_non_expired BOOLEAN NOT NULL DEFAULT TRUE,
    account_non_locked BOOLEAN NOT NULL DEFAULT TRUE,
    credentials_non_expired BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_login TIMESTAMP WITH TIME ZONE,

    CONSTRAINT valid_username CHECK (username ~ '^[a-zA-Z0-9_-]{3,50}$'),
    CONSTRAINT valid_email CHECK (email IS NULL OR email ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,}$')
);

CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email ON users(email);

-- ===========================================================================
-- Table: user_roles
-- User roles for authorization
-- ===========================================================================
CREATE TABLE user_roles (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT valid_role CHECK (role IN ('ROLE_USER', 'ROLE_ADMIN', 'ROLE_API', 'ROLE_READONLY')),
    CONSTRAINT user_role_unique UNIQUE (user_id, role)
);

CREATE INDEX idx_user_roles_user_id ON user_roles(user_id);

-- ===========================================================================
-- Table: oauth2_registered_clients
-- OAuth2 client registration (Spring Authorization Server standard)
-- ===========================================================================
CREATE TABLE oauth2_registered_clients (
    id VARCHAR(100) PRIMARY KEY,
    client_id VARCHAR(100) NOT NULL UNIQUE,
    client_id_issued_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    client_secret VARCHAR(200),
    client_secret_expires_at TIMESTAMP WITH TIME ZONE,
    client_name VARCHAR(200) NOT NULL,
    client_authentication_methods VARCHAR(1000) NOT NULL,
    authorization_grant_types VARCHAR(1000) NOT NULL,
    redirect_uris VARCHAR(1000),
    post_logout_redirect_uris VARCHAR(1000),
    scopes VARCHAR(1000) NOT NULL,
    client_settings VARCHAR(2000) NOT NULL,
    token_settings VARCHAR(2000) NOT NULL
);

CREATE INDEX idx_oauth2_client_id ON oauth2_registered_clients(client_id);

-- ===========================================================================
-- Table: oauth2_authorization
-- OAuth2 authorization tracking
-- ===========================================================================
CREATE TABLE oauth2_authorization (
    id VARCHAR(100) PRIMARY KEY,
    registered_client_id VARCHAR(100) NOT NULL,
    principal_name VARCHAR(200) NOT NULL,
    authorization_grant_type VARCHAR(100) NOT NULL,
    authorized_scopes VARCHAR(1000),
    attributes TEXT,
    state VARCHAR(500),
    authorization_code_value TEXT,
    authorization_code_issued_at TIMESTAMP WITH TIME ZONE,
    authorization_code_expires_at TIMESTAMP WITH TIME ZONE,
    authorization_code_metadata TEXT,
    access_token_value TEXT,
    access_token_issued_at TIMESTAMP WITH TIME ZONE,
    access_token_expires_at TIMESTAMP WITH TIME ZONE,
    access_token_metadata TEXT,
    access_token_type VARCHAR(100),
    access_token_scopes VARCHAR(1000),
    oidc_id_token_value TEXT,
    oidc_id_token_issued_at TIMESTAMP WITH TIME ZONE,
    oidc_id_token_expires_at TIMESTAMP WITH TIME ZONE,
    oidc_id_token_metadata TEXT,
    refresh_token_value TEXT,
    refresh_token_issued_at TIMESTAMP WITH TIME ZONE,
    refresh_token_expires_at TIMESTAMP WITH TIME ZONE,
    refresh_token_metadata TEXT,
    user_code_value TEXT,
    user_code_issued_at TIMESTAMP WITH TIME ZONE,
    user_code_expires_at TIMESTAMP WITH TIME ZONE,
    user_code_metadata TEXT,
    device_code_value TEXT,
    device_code_issued_at TIMESTAMP WITH TIME ZONE,
    device_code_expires_at TIMESTAMP WITH TIME ZONE,
    device_code_metadata TEXT
);

CREATE INDEX idx_oauth2_auth_client ON oauth2_authorization(registered_client_id);
CREATE INDEX idx_oauth2_auth_principal ON oauth2_authorization(principal_name);

-- ===========================================================================
-- Table: oauth2_authorization_consent
-- OAuth2 user consent tracking
-- ===========================================================================
CREATE TABLE oauth2_authorization_consent (
    registered_client_id VARCHAR(100) NOT NULL,
    principal_name VARCHAR(200) NOT NULL,
    authorities VARCHAR(1000) NOT NULL,
    PRIMARY KEY (registered_client_id, principal_name)
);

-- ===========================================================================
-- Comments for documentation
-- ===========================================================================
COMMENT ON TABLE heat_pump_readings IS 'Stores all heat pump data readings from ESP32 device';
COMMENT ON TABLE system_configuration IS 'Key-value configuration storage for runtime settings';
COMMENT ON TABLE users IS 'User authentication and account information';
COMMENT ON TABLE user_roles IS 'User role assignments for authorization';
COMMENT ON TABLE oauth2_registered_clients IS 'OAuth2 client registration information';
COMMENT ON TABLE oauth2_authorization IS 'OAuth2 authorization grants and tokens';
COMMENT ON TABLE oauth2_authorization_consent IS 'User consent for OAuth2 scopes';

COMMENT ON COLUMN heat_pump_readings.raw_json IS 'Full JSON response from ESP32 for debugging and flexibility';
COMMENT ON COLUMN system_configuration.config_key IS 'Configuration key (lowercase with underscores)';
COMMENT ON COLUMN users.password_hash IS 'BCrypt hashed password (strength 12)';
