package com.ilja.smarthome.energycontrol.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Single-row memory of the heating control loop.
 *
 * <p>{@code lastReadBackSetpoint} is what the heat pump reported right after our last write (or when we
 * last confirmed it in sync). If a later reading differs, someone changed the setpoint by hand.
 * Storing the read-back value rather than the value we sent keeps this correct even if the
 * heat pump rounds what it receives.
 */
@Entity
@Table(name = "heating_control_state")
@Getter
@Setter
@NoArgsConstructor
public class HeatingControlState {

    public static final short SINGLETON_ID = 1;

    @Id
    private Short id = SINGLETON_ID;

    @Column(name = "last_desired_setpoint", nullable = false, precision = 4, scale = 2)
    private BigDecimal lastDesiredSetpoint;

    @Column(name = "last_read_back_setpoint", nullable = false, precision = 4, scale = 2)
    private BigDecimal lastReadBackSetpoint;

    /**
     * Value being written right now: set before the Modbus write, cleared once the heat pump confirms it.
     * If the write call fails after the heat pump applied the value, the next run finds it here and does
     * not mistake it for a manual change.
     */
    @Column(name = "pending_setpoint", precision = 4, scale = 2)
    private BigDecimal pendingSetpoint;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
