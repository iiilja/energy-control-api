package com.ilja.smarthome.energycontrol.dto.heating;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

/**
 * One entry of the weekly heating template.
 *
 * @param dayOfWeek 1 = Monday ... 7 = Sunday
 * @param startTime Tallinn wall-clock time the entry starts, "HH:mm" (or "HH:mm:ss")
 * @param setpoint  comfort setpoint in °C, 15–30
 */
public record WeeklyScheduleEntryRequest(
        @NotNull @Min(1) @Max(7)
        Short dayOfWeek,

        @NotNull @Pattern(regexp = "([01]\\d|2[0-3]):[0-5]\\d(:[0-5]\\d)?", message = "must be HH:mm")
        String startTime,

        @NotNull @DecimalMin("15.0") @DecimalMax("30.0")
        BigDecimal setpoint
) {}
