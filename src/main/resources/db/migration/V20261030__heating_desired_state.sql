-- Heating control moves from "execute every schedule row once" to "every 15 minutes compare the
-- desired setpoint with the one on the heat pump and write only when they differ".
--
-- Desired setpoint, highest priority first:
--   1. active manual override (heating_manual_override)
--   2. price-slot override    (heating_setpoint_schedule)
--   3. weekly template        (default_weekly_schedule), read directly
--   4. heating.setpoint.default

-- ---------------------------------------------------------------------------
-- heating_setpoint_schedule becomes "per-slot overrides" only.
-- All rows so far were automatic copies of the weekly template (the UI never wrote per-slot
-- values). Kept, they would hide later template edits, so they are removed.
-- ---------------------------------------------------------------------------
DELETE FROM heating_setpoint_schedule;
DROP INDEX IF EXISTS idx_setpoint_schedule_pending;
ALTER TABLE heating_setpoint_schedule DROP COLUMN applied;

COMMENT ON TABLE heating_setpoint_schedule IS
    'Setpoint overrides for single 15-minute price slots (e.g. from price optimisation); the weekly template applies where there is none';

-- ---------------------------------------------------------------------------
-- Manual overrides: a setpoint changed by hand (on the heat pump panel, or via the API)
-- is kept until valid_until, then control returns to the schedule. Rows are kept as history.
-- ---------------------------------------------------------------------------
CREATE TABLE heating_manual_override (
    id          BIGSERIAL PRIMARY KEY,
    setpoint    DECIMAL(4,2) NOT NULL,
    source      VARCHAR(20) NOT NULL,
    started_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    valid_until TIMESTAMP WITH TIME ZONE NOT NULL,
    ended_at    TIMESTAMP WITH TIME ZONE,

    CONSTRAINT valid_override_source CHECK (source IN ('HEAT_PUMP', 'API')),
    CONSTRAINT valid_override_period CHECK (valid_until > started_at)
);

CREATE INDEX idx_manual_override_open ON heating_manual_override(started_at DESC) WHERE ended_at IS NULL;

COMMENT ON TABLE heating_manual_override IS 'Setpoints changed by hand; kept until valid_until, then the schedule takes over again';
COMMENT ON COLUMN heating_manual_override.source IS 'HEAT_PUMP = change detected on the heat pump, API = set through the REST API';
COMMENT ON COLUMN heating_manual_override.ended_at IS 'When control returned to the schedule (expired, cancelled or replaced); NULL while open';

-- ---------------------------------------------------------------------------
-- Single-row state of the control loop: what we last wanted on the heat pump and what the
-- heat pump reported right after we wrote it. A later reading that differs from
-- last_read_back_setpoint (and from pending_setpoint) means someone changed the setpoint by hand.
-- ---------------------------------------------------------------------------
CREATE TABLE heating_control_state (
    id                      SMALLINT PRIMARY KEY,
    last_desired_setpoint   DECIMAL(4,2) NOT NULL,
    last_read_back_setpoint DECIMAL(4,2) NOT NULL,
    pending_setpoint        DECIMAL(4,2),
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT single_row CHECK (id = 1)
);

COMMENT ON COLUMN heating_control_state.pending_setpoint IS
    'Value being written; set before the write and cleared once confirmed, so a write that failed after the heat pump applied it is not mistaken for a manual change';

INSERT INTO system_configuration (config_key, config_value, description) VALUES
    ('heating.setpoint.manual_override.hours', '4',
     'How long (hours, decimals allowed) a setpoint changed by hand on the heat pump is kept before returning to the schedule')
ON CONFLICT (config_key) DO NOTHING;

UPDATE system_configuration
   SET description = 'Enable or disable the 15-minute heating setpoint control loop'
 WHERE config_key = 'heating.setpoint.adjustment.enabled';
