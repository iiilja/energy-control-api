package com.ilja.smarthome.energycontrol.domain.model;

import io.hypersistence.utils.hibernate.type.array.ListArrayType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.math.BigDecimal;
import java.util.List;

/**
 * Embeddable entity representing heat curve configuration.
 * Mapped from ESP32 'heatCurve' object.
 * Contains arrays of outdoor and supply temperature points.
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HeatCurveData {

    @Type(ListArrayType.class)
    @Column(name = "heat_curve_outdoor_temps", columnDefinition = "decimal(5,2)[]")
    private List<BigDecimal> outdoorTemps;

    @Type(ListArrayType.class)
    @Column(name = "heat_curve_supply_temps", columnDefinition = "decimal(5,2)[]")
    private List<BigDecimal> supplyTemps;
}
