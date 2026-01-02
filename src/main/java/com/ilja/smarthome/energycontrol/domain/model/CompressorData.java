package com.ilja.smarthome.energycontrol.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Embeddable entity representing compressor operation data.
 * Mapped from ESP32 'compressor' object.
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompressorData {

    @Column(name = "compressor_rpm")
    private Integer rpm;

    @Column(name = "compressor_speed")
    private Integer speed;

    @Column(name = "compressor_hours")
    private Integer hours;
}
