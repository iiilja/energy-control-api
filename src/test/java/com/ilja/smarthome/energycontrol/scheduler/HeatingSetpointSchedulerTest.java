package com.ilja.smarthome.energycontrol.scheduler;

import com.ilja.smarthome.energycontrol.domain.model.HeatingSetpointSchedule;
import com.ilja.smarthome.energycontrol.domain.model.NordpoolPrice;
import com.ilja.smarthome.energycontrol.exception.InvalidValueException;
import com.ilja.smarthome.energycontrol.repository.HeatingSetpointScheduleRepository;
import com.ilja.smarthome.energycontrol.service.ConfigurationService;
import com.ilja.smarthome.energycontrol.service.HeatingSetpointService;
import com.ilja.smarthome.energycontrol.thermia.exception.ThermiaCommException;
import com.ilja.smarthome.energycontrol.thermia.service.ThermiaHeatPumpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link HeatingSetpointScheduler}: picks the pending schedule for the current hour (from the injected
 * {@link Clock}), writes it to the heat pump and marks it applied; a rejected value leaves it pending.
 */
@ExtendWith(MockitoExtension.class)
class HeatingSetpointSchedulerTest {

    /** 05:00:20 in Tallinn on Monday 26 Oct 2026 (EET, UTC+2); the clock itself is UTC like the JVM in Docker. */
    private static final Instant NOW = Instant.parse("2026-10-26T03:00:20Z");

    @Mock private ThermiaHeatPumpService thermiaService;
    @Mock private HeatingSetpointService heatingSetpointService;
    @Mock private ConfigurationService configService;
    @Mock private HeatingSetpointScheduleRepository scheduleRepository;

    private HeatingSetpointScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new HeatingSetpointScheduler(thermiaService, heatingSetpointService, configService,
                scheduleRepository, Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(configService.getConfigValue("heating.setpoint.adjustment.enabled", "true")).thenReturn("true");
    }

    @Test
    @DisplayName("writes the pending setpoint, marks it applied and queries with the current hour from the Clock")
    void appliesPendingSetpoint() {
        HeatingSetpointSchedule schedule = schedule("2026-10-26T05:00+02:00[Europe/Tallinn]", "22.0");
        when(scheduleRepository.findNextPendingSchedule(any())).thenReturn(Optional.of(schedule));

        scheduler.applyHourlySetpoint();

        verify(thermiaService).setComfortSetpoint(22.0);
        assertThat(schedule.getApplied()).isTrue();
        verify(scheduleRepository).save(schedule);

        ArgumentCaptor<ZonedDateTime> at = ArgumentCaptor.forClass(ZonedDateTime.class);
        verify(scheduleRepository).findNextPendingSchedule(at.capture());
        assertThat(at.getValue().toInstant()).isEqualTo(Instant.parse("2026-10-26T03:00:00Z"));
    }

    @Test
    @DisplayName("an out-of-range stored setpoint is rejected by the guard and stays pending")
    void rejectedSetpointStaysPending() {
        HeatingSetpointSchedule schedule = schedule("2026-10-26T05:00+02:00[Europe/Tallinn]", "50.0");
        when(scheduleRepository.findNextPendingSchedule(any())).thenReturn(Optional.of(schedule));
        doThrow(new InvalidValueException("Comfort setpoint must be between 15.0 and 30.0 °C, got: 50.0"))
                .when(thermiaService).setComfortSetpoint(50.0);

        scheduler.applyHourlySetpoint();

        assertThat(schedule.getApplied()).isFalse();
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    @DisplayName("heat pump unreachable: schedule stays pending")
    void communicationFailureStaysPending() {
        HeatingSetpointSchedule schedule = schedule("2026-10-26T05:00+02:00[Europe/Tallinn]", "22.0");
        when(scheduleRepository.findNextPendingSchedule(any())).thenReturn(Optional.of(schedule));
        doThrow(new ThermiaCommException("timeout")).when(thermiaService).setComfortSetpoint(22.0);

        scheduler.applyHourlySetpoint();

        assertThat(schedule.getApplied()).isFalse();
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    @DisplayName("disabled via configuration: nothing is read or written")
    void disabledDoesNothing() {
        when(configService.getConfigValue("heating.setpoint.adjustment.enabled", "true")).thenReturn("false");

        scheduler.applyHourlySetpoint();

        verifyNoInteractions(scheduleRepository, thermiaService);
    }

    private static HeatingSetpointSchedule schedule(String slot, String setpoint) {
        NordpoolPrice price = new NordpoolPrice(ZonedDateTime.parse(slot), new BigDecimal("42.0"));
        return new HeatingSetpointSchedule(price, new BigDecimal(setpoint));
    }
}
