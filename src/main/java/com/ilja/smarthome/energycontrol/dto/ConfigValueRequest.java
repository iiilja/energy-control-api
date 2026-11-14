package com.ilja.smarthome.energycontrol.dto;

import jakarta.validation.constraints.NotNull;

public record ConfigValueRequest(@NotNull String value) {
}
