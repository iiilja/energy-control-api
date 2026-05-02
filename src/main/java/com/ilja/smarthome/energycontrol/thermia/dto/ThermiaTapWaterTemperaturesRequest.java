package com.ilja.smarthome.energycontrol.thermia.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/** Request body for configuring tap water start/stop temperatures. */
public record ThermiaTapWaterTemperaturesRequest(
        @NotNull @DecimalMin("30.0") @DecimalMax("60.0")
        Double startTemperature,

        @NotNull @DecimalMin("30.0") @DecimalMax("65.0")
        Double stopTemperature
) {}
