package com.ilja.smarthome.energycontrol.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Embeddable entity representing temperature readings from the heat pump.
 * Mapped from ESP32 'temperatures' object.
 * Uses BigDecimal for precise temperature values.
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TemperatureData {

    @Column(name = "outdoor_temp", precision = 5, scale = 2)
    private BigDecimal outdoorTemp;

    @Column(name = "brine_in_temp", precision = 5, scale = 2)
    private BigDecimal brineInTemp;

    @Column(name = "brine_out_temp", precision = 5, scale = 2)
    private BigDecimal brineOutTemp;

    @Column(name = "system_supply_in_temp", precision = 5, scale = 2)
    private BigDecimal systemSupplyInTemp;

    @Column(name = "system_supply_out_temp", precision = 5, scale = 2)
    private BigDecimal systemSupplyOutTemp;

    @Column(name = "system_supply_line_temp", precision = 5, scale = 2)
    private BigDecimal systemSupplyLineTemp;

    @Column(name = "system_supply_setpoint", precision = 5, scale = 2)
    private BigDecimal systemSupplySetpoint;

    @Column(name = "tap_water_top_temp", precision = 5, scale = 2)
    private BigDecimal tapWaterTopTemp;

    @Column(name = "tap_water_lower_temp", precision = 5, scale = 2)
    private BigDecimal tapWaterLowerTemp;
}
