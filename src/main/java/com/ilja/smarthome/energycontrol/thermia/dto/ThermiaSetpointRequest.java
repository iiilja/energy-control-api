package com.ilja.smarthome.energycontrol.thermia.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/** Request body for changing the comfort wheel (desired indoor temperature). */
public record ThermiaSetpointRequest(
        @NotNull @DecimalMin("15.0") @DecimalMax("30.0")
        Double temperature
) {}
