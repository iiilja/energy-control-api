-- V5: Default weekly heating setpoint schedule

CREATE TABLE default_weekly_schedule (
    id BIGSERIAL PRIMARY KEY,
    day_of_week SMALLINT NOT NULL,
    start_time TIME NOT NULL,
    setpoint DECIMAL(4,2) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT unique_day_time UNIQUE (day_of_week, start_time),
    CONSTRAINT valid_day_of_week CHECK (day_of_week BETWEEN 1 AND 7)
);

CREATE INDEX idx_weekly_schedule_day_time ON default_weekly_schedule(day_of_week, start_time);

COMMENT ON TABLE default_weekly_schedule IS 'Default weekly heating setpoint schedule template (1=Monday, 7=Sunday)';
COMMENT ON COLUMN default_weekly_schedule.day_of_week IS 'Day of week: 1=Monday, 2=Tuesday, ... 7=Sunday';
COMMENT ON COLUMN default_weekly_schedule.start_time IS 'Start time for this setpoint (applies until next entry)';
COMMENT ON COLUMN default_weekly_schedule.setpoint IS 'Target heating setpoint in degrees Celsius';

-- Monday (1) - Friday (5): 00:00-05:00: 20°C, 05:00-08:00: 22°C, 08:00-24:00: 21°C
INSERT INTO default_weekly_schedule (day_of_week, start_time, setpoint) VALUES
-- Monday
(1, '00:00', 20.0),
(1, '05:00', 22.0),
(1, '08:00', 21.0),

-- Tuesday
(2, '00:00', 20.0),
(2, '05:00', 22.0),
(2, '08:00', 21.0),

-- Wednesday
(3, '00:00', 20.0),
(3, '05:00', 22.0),
(3, '08:00', 21.0),

-- Thursday
(4, '00:00', 20.0),
(4, '05:00', 22.0),
(4, '08:00', 21.0),

-- Friday
(5, '00:00', 20.0),
(5, '05:00', 22.0),
(5, '08:00', 21.0),

-- Saturday (6): 00:00-05:00: 20°C, 05:00-24:00: 22°C
(6, '00:00', 20.0),
(6, '05:00', 22.0),

-- Sunday (7): 00:00-05:00: 20°C, 05:00-24:00: 22°C
(7, '00:00', 20.0),
(7, '05:00', 22.0);
