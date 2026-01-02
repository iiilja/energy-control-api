package com.ilja.smarthome.energycontrol.dto.esp32;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SetpointRequest {
    private Double temperature;
}
