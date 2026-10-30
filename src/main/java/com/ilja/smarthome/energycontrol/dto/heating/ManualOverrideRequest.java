package com.ilja.smarthome.energycontrol.dto.heating;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Manual setpoint override, e.g. "guests arrived, make it warmer".
 *
 * @param setpoint comfort setpoint in °C, 15–30
 * @param hours    how long to keep it (0.25–24); omit to use {@code heating.setpoint.manual_override.hours}
 */
public record ManualOverrideRequest(
        @NotNull @DecimalMin("15.0") @DecimalMax("30.0")
        BigDecimal setpoint,

        @DecimalMin("0.25") @DecimalMax("24.0")
        Double hours
) {}
