-- ESP32 relay pump control columns are no longer collected; heat pump is now
-- read directly via Modbus TCP and has no circulation pump relay data.
ALTER TABLE heat_pump_readings
    DROP COLUMN IF EXISTS pump_auto_mode,
    DROP COLUMN IF EXISTS pump_current_state,
    DROP COLUMN IF EXISTS pump_manual_state,
    DROP COLUMN IF EXISTS pump_on_duration,
    DROP COLUMN IF EXISTS pump_off_duration,
    DROP COLUMN IF EXISTS pump_last_state_change,
    DROP COLUMN IF EXISTS pump_remaining_minutes;
