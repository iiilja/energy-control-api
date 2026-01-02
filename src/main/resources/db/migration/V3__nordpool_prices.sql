-- V3: Nordpool price data tables
-- Creates tables for storing electricity prices from Nordpool/Elering API (Estonia region only)

-- ===========================================================================
-- Table: nordpool_prices
-- Stores electricity price data for 15-minute intervals (EE region)
-- ===========================================================================
CREATE TABLE nordpool_prices (
    id BIGSERIAL PRIMARY KEY,
    price_timestamp TIMESTAMP WITH TIME ZONE NOT NULL UNIQUE,
    price DECIMAL(10,4) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Indexes for common query patterns
CREATE INDEX idx_nordpool_prices_timestamp ON nordpool_prices(price_timestamp DESC);

-- ===========================================================================
-- Table: heating_setpoint_schedule
-- Stores planned heating setpoint adjustments linked to Nordpool prices
-- ===========================================================================
CREATE TABLE heating_setpoint_schedule (
    id BIGSERIAL PRIMARY KEY,
    nordpool_price_id BIGINT NOT NULL REFERENCES nordpool_prices(id) ON DELETE CASCADE,
    target_setpoint DECIMAL(4,2) NOT NULL,
    applied BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT unique_price_schedule UNIQUE (nordpool_price_id)
);

CREATE INDEX idx_setpoint_schedule_price ON heating_setpoint_schedule(nordpool_price_id);
CREATE INDEX idx_setpoint_schedule_pending ON heating_setpoint_schedule(applied)
    WHERE applied = FALSE;

-- ===========================================================================
-- Comments for documentation
-- ===========================================================================
COMMENT ON TABLE nordpool_prices IS 'Nordpool electricity price data for Estonia (EE) region (15-minute intervals)';
COMMENT ON COLUMN nordpool_prices.price_timestamp IS 'Start time of the 15-minute price period';
COMMENT ON COLUMN nordpool_prices.price IS 'Electricity price in EUR/MWh';

COMMENT ON TABLE heating_setpoint_schedule IS 'Scheduled heating setpoint adjustments linked to Nordpool price entries';
COMMENT ON COLUMN heating_setpoint_schedule.nordpool_price_id IS 'Reference to nordpool_prices table';
COMMENT ON COLUMN heating_setpoint_schedule.target_setpoint IS 'Target heating setpoint in degrees Celsius';
COMMENT ON COLUMN heating_setpoint_schedule.applied IS 'Whether this scheduled adjustment has been applied';


INSERT INTO system_configuration (config_key, config_value, description) VALUES
('nordpool.fetch.enabled', 'true', 'Enable or disable automatic Nordpool price fetching'),
('nordpool.cleanup.enabled', 'true', 'Enable or disable automatic cleanup of old Nordpool prices'),
('nordpool.cleanup.days_to_keep', '30', 'Number of days of Nordpool price data to keep'),
('heating.setpoint.default', '21.0', 'Default heating setpoint in degrees Celsius'),
('heating.setpoint.adjustment.enabled', 'true', 'Enable or disable automatic hourly heating setpoint adjustments');
