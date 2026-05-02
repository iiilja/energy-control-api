package com.ilja.smarthome.energycontrol.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Main entity representing a complete heat pump data reading.
 */
@Entity
@Table(name = "heat_pump_readings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HeatPumpReading {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "collection_timestamp", nullable = false, unique = true)
    private LocalDateTime collectionTimestamp;

    @Embedded
    private StatusData status;

    @Embedded
    private TemperatureData temperatures;

    @Embedded
    private CompressorData compressor;

    @Embedded
    private HeatingData heating;

    @Embedded
    private HeatCurveData heatCurve;

    /**
     * Called before persisting a new entity.
     * Sets the timestamp to current time if not already set.
     */
    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }
}
