package com.ilja.smarthome.energycontrol.sungrow.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for setting the inverter feed-in power limit.
 * Provide either {@code percent} or {@code kw} — not both.
 * If both are given, {@code kw} takes precedence.
 */
public record SungrowPowerLimitRequest(

        @DecimalMin("0.0") @DecimalMax("110.0")
        Double percent,

        @DecimalMin("0.0")
        Double kw
) {
    @NotNull
    public boolean hasKw() {
        return kw != null;
    }
}
