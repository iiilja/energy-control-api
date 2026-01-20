package com.ilja.smarthome.energycontrol.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.ZonedDateTime;

@Entity
@Table(name = "default_weekly_schedule")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DefaultWeeklySchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "day_of_week", nullable = false)
    private Short dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "setpoint", nullable = false, precision = 4, scale = 2)
    private BigDecimal setpoint;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = ZonedDateTime.now();
    }

    public DefaultWeeklySchedule(Short dayOfWeek, LocalTime startTime, BigDecimal setpoint) {
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.setpoint = setpoint;
    }
}
