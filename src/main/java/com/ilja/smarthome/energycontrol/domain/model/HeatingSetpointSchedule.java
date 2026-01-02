package com.ilja.smarthome.energycontrol.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Entity
@Table(name = "heating_setpoint_schedule")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HeatingSetpointSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nordpool_price_id", nullable = false)
    private NordpoolPrice nordpoolPrice;

    @Column(name = "target_setpoint", nullable = false, precision = 4, scale = 2)
    private BigDecimal targetSetpoint;

    @Column(name = "applied", nullable = false)
    private Boolean applied = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = ZonedDateTime.now();
    }

    public HeatingSetpointSchedule(NordpoolPrice nordpoolPrice, BigDecimal targetSetpoint) {
        this.nordpoolPrice = nordpoolPrice;
        this.targetSetpoint = targetSetpoint;
        this.applied = false;
    }
}
