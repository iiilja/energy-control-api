package com.ilja.smarthome.energycontrol.dto.heating;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SetpointScheduleItemResponse {
    private ZonedDateTime timestamp;
    private BigDecimal price;
    private BigDecimal setpoint;
    private Long nordpoolPriceId;
}
