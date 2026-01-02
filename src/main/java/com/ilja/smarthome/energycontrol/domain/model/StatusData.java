package com.ilja.smarthome.energycontrol.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Embeddable entity representing heat pump status information.
 * Mapped from ESP32 'status' object.
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StatusData {

    @Column(name = "connected")
    private Boolean connected;

    @Column(name = "last_update")
    private Long lastUpdate;

    @Column(name = "operation_mode")
    private Short operationMode;

    @Column(name = "operation_mode_text", length = 20)
    private String operationModeText;

    @Column(name = "alarm_active")
    private Boolean alarmActive;

    @Column(name = "compressor_running")
    private Boolean compressorRunning;

    @Column(name = "current_demand", columnDefinition = "smallint")
    private Short currentDemand;

    @Column(name = "current_demand_text", length = 50)
    private String currentDemandText;
}
