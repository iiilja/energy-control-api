package com.ilja.smarthome.energycontrol.dto.heating;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SetpointScheduleItemRequest {
    private Long nordpoolPriceId;
    private BigDecimal setpoint;
}
