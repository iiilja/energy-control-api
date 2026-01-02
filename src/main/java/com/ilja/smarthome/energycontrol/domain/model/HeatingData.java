package com.ilja.smarthome.energycontrol.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Embeddable entity representing heating system data.
 * Mapped from ESP32 'heating' object.
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HeatingData {

    @Column(name = "heating_setpoint", precision = 4, scale = 2)
    private BigDecimal setpoint;

    @Column(name = "heating_hours")
    private Integer hours;

    @Column(name = "external_heater_hours")
    private Integer externalHeaterHours;
}
