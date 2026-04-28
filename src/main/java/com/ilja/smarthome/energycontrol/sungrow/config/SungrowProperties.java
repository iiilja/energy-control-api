package com.ilja.smarthome.energycontrol.sungrow.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "sungrow")
public class SungrowProperties {

    /** Inverter IP address or hostname */
    @NotBlank
    private String host = "192.168.8.110";

    /** Modbus TCP port (502 = plain, 8899 = some firmwares) */
    @Min(1) @Max(65535)
    private int port = 502;

    /** Modbus slave ID */
    @Min(1) @Max(247)
    private int slaveId = 1;

    /** Socket connection timeout in milliseconds */
    @Min(1000)
    private int timeoutMs = 5000;

    /** Rated power in kW — used for percent/kW conversions */
    @Min(1)
    private int ratedPowerKw = 15;
}
