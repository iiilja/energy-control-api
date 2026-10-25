package com.ilja.smarthome.energycontrol.dto.heating;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SetpointScheduleItemRequest {
    @NotNull
    private Long nordpoolPriceId;

    @NotNull @DecimalMin("15.0") @DecimalMax("30.0")
    private BigDecimal setpoint;
}
