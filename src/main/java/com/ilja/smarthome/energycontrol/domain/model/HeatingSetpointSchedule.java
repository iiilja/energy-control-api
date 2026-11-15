package com.ilja.smarthome.energycontrol.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

/** Setpoint override for one 15-minute price slot; the weekly template applies where there is none. */
@Entity
@Table(name = "heating_setpoint_schedule")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HeatingSetpointSchedule {

    public enum Source {
        /** Set through the API; the price optimisation leaves these slots alone. */
        MANUAL,
        /** Planned by the price optimisation; replaced on every run. */
        PRICE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nordpool_price_id", nullable = false)
    private NordpoolPrice nordpoolPrice;

    @Column(name = "target_setpoint", nullable = false, precision = 4, scale = 2)
    private BigDecimal targetSetpoint;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private Source source = Source.MANUAL;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = ZonedDateTime.now();
    }

    public HeatingSetpointSchedule(NordpoolPrice nordpoolPrice, BigDecimal targetSetpoint) {
        this.nordpoolPrice = nordpoolPrice;
        this.targetSetpoint = targetSetpoint;
    }

    public HeatingSetpointSchedule(NordpoolPrice nordpoolPrice, BigDecimal targetSetpoint, Source source) {
        this.nordpoolPrice = nordpoolPrice;
        this.targetSetpoint = targetSetpoint;
        this.source = source;
    }
}
