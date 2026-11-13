package com.ilja.smarthome.energycontrol.thermia.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "thermia")
public class ThermiaProperties {

    /** Heat pump IP address or hostname */
    @NotBlank
    private String host = "192.168.8.126";

    /** Modbus TCP port */
    @Min(1) @Max(65535)
    private int port = 502;

    /** Modbus slave ID */
    @Min(1) @Max(247)
    private int slaveId = 1;

    /** Socket connection timeout in milliseconds */
    @Min(1000)
    private int timeoutMs = 5000;

    /** How long a caller waits for its turn when another Modbus transaction is in progress */
    @Min(1000)
    private int lockTimeoutMs = 30000;

    /** GET /api/v1/thermia/data serves a snapshot up to this old; data collection refreshes it every minute (fixed delay), so 2 min covers a late refresh; 0 = always read live */
    @Min(0)
    private long snapshotMaxAgeMs = 120000;
}
