package com.ilja.smarthome.energycontrol.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A heating setpoint set by hand, kept until {@link #validUntil}, after which the schedule takes over again.
 * Rows are never deleted; {@link #endedAt} marks when the override stopped (expired, cancelled or replaced).
 */
@Entity
@Table(name = "heating_manual_override")
@Getter
@Setter
@NoArgsConstructor
public class HeatingManualOverride {

    public enum Source {
        /** The setpoint was changed on the heat pump itself (detected by the control loop). */
        HEAT_PUMP,
        /** The setpoint was set through the REST API (e.g. a "guests" button in the UI). */
        API
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "setpoint", nullable = false, precision = 4, scale = 2)
    private BigDecimal setpoint;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private Source source;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "valid_until", nullable = false)
    private Instant validUntil;

    @Column(name = "ended_at")
    private Instant endedAt;

    public HeatingManualOverride(BigDecimal setpoint, Source source, Instant startedAt, Instant validUntil) {
        this.setpoint = setpoint;
        this.source = source;
        this.startedAt = startedAt;
        this.validUntil = validUntil;
    }

    /** True while the override applies at {@code now}: not ended and not yet expired. */
    public boolean isActiveAt(Instant now) {
        return endedAt == null && now.isBefore(validUntil);
    }
}
