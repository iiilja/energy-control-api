package com.ilja.smarthome.energycontrol.thermia.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for changing the heat pump operation mode.
 * mode: 1=OFF, 2=Standby, 3=ON/Auto
 */
public record ThermiaOperationModeRequest(
        @NotNull @Min(1) @Max(3)
        Integer mode
) {}
