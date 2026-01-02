package com.ilja.smarthome.energycontrol.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Embeddable entity representing hot water circulation pump data.
 * Mapped from ESP32 'pump' object.
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PumpData {

    @Column(name = "pump_auto_mode")
    private Boolean autoMode;

    @Column(name = "pump_current_state")
    private Boolean currentState;

    @Column(name = "pump_manual_state")
    private Boolean manualState;

    @Column(name = "pump_on_duration")
    private Integer onDuration;

    @Column(name = "pump_off_duration")
    private Integer offDuration;

    @Column(name = "pump_last_state_change")
    private Long lastStateChange;

    @Column(name = "pump_remaining_minutes")
    private Integer remainingMinutes;
}
