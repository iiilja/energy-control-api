package com.ilja.smarthome.energycontrol.dto.heating;

import com.ilja.smarthome.energycontrol.domain.model.HeatingManualOverride;

import java.math.BigDecimal;
import java.time.Instant;

/** The manual setpoint override currently in effect. */
public record ManualOverrideResponse(
        BigDecimal setpoint,
        HeatingManualOverride.Source source,
        Instant startedAt,
        Instant validUntil
) {
    public static ManualOverrideResponse from(HeatingManualOverride o) {
        return new ManualOverrideResponse(o.getSetpoint(), o.getSource(), o.getStartedAt(), o.getValidUntil());
    }
}
