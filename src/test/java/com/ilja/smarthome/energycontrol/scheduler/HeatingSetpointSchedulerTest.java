package com.ilja.smarthome.energycontrol.scheduler;

import com.ilja.smarthome.energycontrol.service.ConfigurationService;
import com.ilja.smarthome.energycontrol.service.HeatingControlService;
import com.ilja.smarthome.energycontrol.thermia.exception.ThermiaCommException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** {@link HeatingSetpointScheduler}: runs the control loop when enabled and never lets an error stop the schedule. */
@ExtendWith(MockitoExtension.class)
class HeatingSetpointSchedulerTest {

    @Mock private HeatingControlService heatingControlService;
    @Mock private ConfigurationService configService;

    private HeatingSetpointScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new HeatingSetpointScheduler(heatingControlService, configService);
    }

    @Test
    void runsControlLoopWhenEnabled() {
        enabled("true");

        scheduler.controlSetpoint();

        verify(heatingControlService).reconcile();
    }

    @Test
    @DisplayName("disabled via configuration: the heat pump is not touched")
    void disabledDoesNothing() {
        enabled("false");

        scheduler.controlSetpoint();

        verifyNoInteractions(heatingControlService);
    }

    @Test
    @DisplayName("heat pump unreachable or unexpected error: logged, not thrown (the next run retries)")
    void errorsDoNotEscape() {
        enabled("true");
        doThrow(new ThermiaCommException("timeout")).doThrow(new IllegalStateException("boom"))
                .when(heatingControlService).reconcile();

        assertThatCode(() -> scheduler.controlSetpoint()).doesNotThrowAnyException();
        assertThatCode(() -> scheduler.controlSetpoint()).doesNotThrowAnyException();
    }

    private void enabled(String value) {
        when(configService.getConfigValue("heating.setpoint.adjustment.enabled", "true")).thenReturn(value);
    }
}
