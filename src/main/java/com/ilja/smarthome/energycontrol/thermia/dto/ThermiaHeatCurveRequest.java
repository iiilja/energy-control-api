package com.ilja.smarthome.energycontrol.thermia.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request body for updating the heat curve Y-axis (supply temperature) points.
 * Must contain exactly 7 values in °C, each in range 15–65.
 * The 7 points correspond to the 7 outdoor temperature X-axis points stored in the heat pump.
 */
public record ThermiaHeatCurveRequest(
        @NotNull @Size(min = 7, max = 7)
        List<@NotNull @DecimalMin("15.0") @DecimalMax("65.0") Double> points
) {}
