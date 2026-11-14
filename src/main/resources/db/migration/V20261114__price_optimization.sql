-- Price optimisation: cut the price peaks of a day. Slots priced at least peak_threshold EUR/MWh above
-- the day's median get a setpoint lowered below the weekly template; the slots just before (or after)
-- each peak get it raised to compensate. The plan is written as price-slot overrides
-- (heating_setpoint_schedule) with source = 'PRICE'; slots set by hand (source = 'MANUAL') are never
-- touched by it. Off by default.

ALTER TABLE heating_setpoint_schedule
    ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    ADD CONSTRAINT valid_slot_override_source CHECK (source IN ('MANUAL', 'PRICE'));

COMMENT ON COLUMN heating_setpoint_schedule.source IS
    'MANUAL = set through the API, PRICE = planned by the price optimisation (replaced on every run)';

INSERT INTO system_configuration (config_key, config_value, description) VALUES
    ('heating.price_optimization.enabled', 'false',
     'Lower the heating setpoint in price peaks and raise it next to them to compensate'),
    ('heating.price_optimization.peak_threshold', '50',
     'A 15-minute slot is a peak when its price is at least this many EUR/MWh above the day''s median'),
    ('heating.price_optimization.lower', '1.0',
     'Degrees below the weekly template in peak slots (0-3)'),
    ('heating.price_optimization.raise', '1.0',
     'Degrees above the weekly template in compensation slots (0-3)'),
    ('heating.price_optimization.compensation_minutes', '60',
     'Minutes right before or after each peak (whichever is cheaper) with the raised setpoint (0-240)')
ON CONFLICT (config_key) DO NOTHING;
