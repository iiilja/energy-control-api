package com.ilja.smarthome.energycontrol.domain.model;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;

/**
 * Main entity representing a complete heat pump data reading.
 * Stores all data collected from the ESP32 device at a specific point in time.
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

    @Embedded
    private PumpData pump;

    // Raw JSON for flexibility and debugging
    @Type(JsonBinaryType.class)
    @Column(name = "raw_json", columnDefinition = "jsonb")
    private String rawJson;

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
