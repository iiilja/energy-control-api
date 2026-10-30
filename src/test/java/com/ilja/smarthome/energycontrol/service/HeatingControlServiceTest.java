package com.ilja.smarthome.energycontrol.service;

import com.ilja.smarthome.energycontrol.domain.model.HeatingControlState;
import com.ilja.smarthome.energycontrol.domain.model.HeatingManualOverride;
import com.ilja.smarthome.energycontrol.domain.model.HeatingManualOverride.Source;
import com.ilja.smarthome.energycontrol.exception.InvalidValueException;
import com.ilja.smarthome.energycontrol.repository.HeatingControlStateRepository;
import com.ilja.smarthome.energycontrol.repository.HeatingManualOverrideRepository;
import com.ilja.smarthome.energycontrol.thermia.exception.ThermiaCommException;
import com.ilja.smarthome.energycontrol.thermia.service.ThermiaHeatPumpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The heating control loop: write the desired setpoint only when needed, detect a setpoint changed by hand
 * on the heat pump and keep it for the configured time, then return to the schedule.
 */
@ExtendWith(MockitoExtension.class)
class HeatingControlServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-26T08:15:15Z");

    @Mock private ThermiaHeatPumpService thermia;
    @Mock private HeatingSetpointService schedule;
    @Mock private HeatingManualOverrideRepository overrides;
    @Mock private HeatingControlStateRepository stateRepository;
    @Mock private ConfigurationService config;

    private HeatingControlService service;

    @BeforeEach
    void setUp() {
        service = new HeatingControlService(thermia, schedule, overrides, stateRepository, config,
                Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(config.getConfigValue("heating.setpoint.manual_override.hours", "4.0")).thenReturn("4");
        lenient().when(overrides.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(overrides.findOpen()).thenReturn(Optional.empty());
        lenient().when(stateRepository.findById(HeatingControlState.SINGLETON_ID)).thenReturn(Optional.empty());
    }

    @Nested
    @DisplayName("following the schedule")
    class FollowingSchedule {

        @Test
        @DisplayName("first run, heat pump already at the scheduled value: no write, state remembered")
        void firstRunInSync() {
            pumpReads(21.0);
            scheduled("21.0");

            service.reconcile();

            verify(thermia, never()).setComfortSetpoint(anyDouble());
            assertLastState("21.0", "21.0");
        }

        @Test
        @DisplayName("first run, heat pump differs: the schedule is written (nothing is known about manual changes yet)")
        void firstRunWritesSchedule() {
            pumpReads(20.0, 22.0);
            scheduled("22.0");

            service.reconcile();

            verify(thermia).setComfortSetpoint(22.0);
            assertLastState("22.0", "22.0");
        }

        @Test
        @DisplayName("nothing changed: no write")
        void inSync() {
            givenState("21.0", "21.0");
            pumpReads(21.0);
            scheduled("21.0");

            service.reconcile();

            verify(thermia, never()).setComfortSetpoint(anyDouble());
        }

        @Test
        @DisplayName("the schedule moves on (05:00 -> 22 °C): written once")
        void scheduleChangeIsWritten() {
            givenState("20.0", "20.0");
            pumpReads(20.0, 22.0);
            scheduled("22.0");

            service.reconcile();

            verify(thermia).setComfortSetpoint(22.0);
            assertLastState("22.0", "22.0");
        }

        @Test
        @DisplayName("heat pump rounds the written value: not rewritten every run, not taken for a manual change")
        void pumpRounding() {
            givenState("21.3", "21.5");
            pumpReads(21.5);
            scheduled("21.3");

            service.reconcile();

            verify(thermia, never()).setComfortSetpoint(anyDouble());
            verify(overrides, never()).save(any());
        }

        @Test
        @DisplayName("read-back after a write fails: our own write is not later mistaken for a manual change")
        void readBackFailure() {
            givenState("20.0", "20.0");
            when(thermia.getComfortSetpoint())
                    .thenReturn(20.0)                                  // run 1: before write
                    .thenThrow(new ThermiaCommException("timeout"))    // run 1: read-back
                    .thenReturn(22.0);                                 // run 2
            scheduled("22.0");

            service.reconcile();
            service.reconcile();

            verify(thermia).setComfortSetpoint(22.0); // once, in run 1
            verify(overrides, never()).save(any());
        }

        @Test
        @DisplayName("write call fails after the heat pump applied it: next run confirms it, no false manual change")
        void writeFailsAfterPumpApplied() {
            givenState("20.0", "20.0");
            pumpReads(20.0, 22.0);                     // run 1 before write, run 2
            scheduled("22.0");
            doThrow(new ThermiaCommException("connection reset")).when(thermia).setComfortSetpoint(22.0);

            assertThatThrownBy(() -> service.reconcile()).isInstanceOf(ThermiaCommException.class);
            service.reconcile();

            verify(thermia).setComfortSetpoint(22.0);  // not rewritten in run 2
            verify(overrides, never()).save(any());
            assertLastState("22.0", "22.0");
        }

        @Test
        @DisplayName("write call fails and the heat pump did not apply it: rewritten on the next run")
        void writeFailsNotApplied() {
            givenState("20.0", "20.0");
            pumpReads(20.0, 20.0, 22.0);               // run 1 before write, run 2, run 2 read-back
            scheduled("22.0");
            doThrow(new ThermiaCommException("timeout")).doNothing().when(thermia).setComfortSetpoint(22.0);

            assertThatThrownBy(() -> service.reconcile()).isInstanceOf(ThermiaCommException.class);
            service.reconcile();

            verify(thermia, times(2)).setComfortSetpoint(22.0);
            verify(overrides, never()).save(any());
            assertLastState("22.0", "22.0");
        }

        @Test
        @DisplayName("scheduled value rejected by the write guard: heat pump untouched, no exception")
        void guardRejects() {
            givenState("21.0", "21.0");
            pumpReads(21.0);
            scheduled("50.0");
            doThrow(new InvalidValueException("Comfort setpoint must be between 15.0 and 30.0 °C, got: 50.0"))
                    .when(thermia).setComfortSetpoint(50.0);

            service.reconcile();

            assertLastState("21.0", "21.0");
        }

        @Test
        @DisplayName("heat pump unreachable: exception propagates (scheduler logs it), nothing saved")
        void pumpUnreachable() {
            when(thermia.getComfortSetpoint()).thenThrow(new ThermiaCommException("timeout"));

            assertThatThrownBy(() -> service.reconcile()).isInstanceOf(ThermiaCommException.class);

            verify(stateRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("setpoint changed by hand on the heat pump")
    class ChangedByHand {

        @Test
        @DisplayName("is detected and kept for the configured 4 hours instead of being overwritten")
        void detected() {
            givenState("21.0", "21.0");
            pumpReads(23.0);

            service.reconcile();

            verify(thermia, never()).setComfortSetpoint(anyDouble());
            HeatingManualOverride created = lastSavedOverride();
            assertThat(created.getSetpoint()).isEqualByComparingTo("23.0");
            assertThat(created.getSource()).isEqualTo(Source.HEAT_PUMP);
            assertThat(created.getStartedAt()).isEqualTo(NOW);
            // counted from the start of the minute, so the run 4 h later (at :15 s) reliably ends it
            assertThat(created.getValidUntil()).isEqualTo(Instant.parse("2026-10-26T12:15:00Z"));
            assertLastState("23.0", "23.0");
        }

        @Test
        @DisplayName("while the override is active the manual value stays, the schedule is not consulted")
        void keptWhileActive() {
            givenState("23.0", "23.0");
            givenOpenOverride("23.0", Source.HEAT_PUMP, NOW.plus(Duration.ofHours(2)));
            pumpReads(23.0);

            service.reconcile();

            verify(thermia, never()).setComfortSetpoint(anyDouble());
            verify(schedule, never()).scheduledSetpointAt(any());
        }

        @Test
        @DisplayName("changed again during the override: new value, timer restarts")
        void changedAgainRestartsTimer() {
            givenState("23.0", "23.0");
            HeatingManualOverride open = givenOpenOverride("23.0", Source.HEAT_PUMP, NOW.plus(Duration.ofHours(1)));
            pumpReads(24.0);

            service.reconcile();

            assertThat(open.getSetpoint()).isEqualByComparingTo("24.0");
            assertThat(open.getValidUntil()).isEqualTo(Instant.parse("2026-10-26T12:15:00Z"));
            verify(thermia, never()).setComfortSetpoint(anyDouble());
        }

        @Test
        @DisplayName("after the override expires the schedule is written back")
        void expiryReturnsToSchedule() {
            givenState("23.0", "23.0");
            HeatingManualOverride open = givenOpenOverride("23.0", Source.HEAT_PUMP, NOW.minus(Duration.ofMinutes(1)));
            pumpReads(23.0, 21.0);
            scheduled("21.0");

            service.reconcile();

            assertThat(open.getEndedAt()).isEqualTo(NOW);
            verify(thermia).setComfortSetpoint(21.0);
            assertLastState("21.0", "21.0");
        }

        @Test
        @DisplayName("changed by hand just before the override expired: becomes a new override, not overwritten")
        void changeAtExpiryBecomesNewOverride() {
            givenState("23.0", "23.0");
            HeatingManualOverride open = givenOpenOverride("23.0", Source.HEAT_PUMP, NOW.minus(Duration.ofMinutes(1)));
            pumpReads(24.0);

            service.reconcile();

            assertThat(open.getEndedAt()).isEqualTo(NOW);
            HeatingManualOverride created = lastSavedOverride();
            assertThat(created).isNotSameAs(open);
            assertThat(created.getSetpoint()).isEqualByComparingTo("24.0");
            verify(thermia, never()).setComfortSetpoint(anyDouble());
        }

        @ParameterizedTest(name = "config ''{0}'' -> {1} minutes")
        @CsvSource({"2.5, 150", "4, 240", "0.25, 15", "abc, 240", "0, 240", "0.001, 240", "-1, 240", "1000, 240"})
        void overrideDurationFromConfig(String configValue, long expectedMinutes) {
            when(config.getConfigValue("heating.setpoint.manual_override.hours", "4.0")).thenReturn(configValue);

            assertThat(service.overrideDuration()).isEqualTo(Duration.ofMinutes(expectedMinutes));
        }
    }

    @Nested
    @DisplayName("override via the API")
    class ApiOverride {

        @Test
        @DisplayName("is written to the heat pump right away and not mistaken for a manual change")
        void startWritesImmediately() {
            givenState("21.0", "21.0");
            when(overrides.findOpen())
                    .thenReturn(Optional.empty())                         // startOverride: nothing to replace
                    .thenReturn(Optional.of(apiOverride("24.0")));        // reconcile + result: the new one
            pumpReads(21.0, 21.0, 24.0);                                  // acknowledge, reconcile, read-back

            HeatingManualOverride created = service.startOverride(new BigDecimal("24.0"), 2.0);

            assertThat(created.getSource()).isEqualTo(Source.API);
            assertThat(created.getValidUntil()).isEqualTo(NOW.plus(Duration.ofHours(2)));
            verify(thermia).setComfortSetpoint(24.0);
            assertLastState("24.0", "24.0");
        }

        @Test
        @DisplayName("replaces an open override")
        void replacesOpen() {
            HeatingManualOverride old = new HeatingManualOverride(new BigDecimal("23.0"), Source.HEAT_PUMP,
                    NOW.minus(Duration.ofHours(1)), NOW.plus(Duration.ofHours(3)));
            when(overrides.findOpen()).thenReturn(Optional.of(old)).thenReturn(Optional.of(apiOverride("24.0")));
            givenState("23.0", "23.0");
            pumpReads(23.0, 23.0, 24.0);

            service.startOverride(new BigDecimal("24.0"), null);

            assertThat(old.getEndedAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("cancel ends it and writes the schedule back right away")
        void cancel() {
            HeatingManualOverride open = givenOpenOverride("24.0", Source.API, NOW.plus(Duration.ofHours(3)));
            when(overrides.findOpen()).thenReturn(Optional.of(open)).thenReturn(Optional.empty());
            givenState("24.0", "24.0");
            pumpReads(24.0, 24.0, 21.0);
            scheduled("21.0");

            assertThat(service.cancelOverride()).isTrue();

            assertThat(open.getEndedAt()).isEqualTo(NOW);
            verify(thermia).setComfortSetpoint(21.0);
        }

        @Test
        @DisplayName("POST right after an unnoticed panel change: the API value wins, no HEAT_PUMP override")
        void startAfterUndetectedPanelChange() {
            givenState("21.0", "21.0");
            when(overrides.findOpen())
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.of(apiOverride("24.0")));
            pumpReads(23.0, 23.0, 24.0);   // panel was set to 23 after the last run

            service.startOverride(new BigDecimal("24.0"), 2.0);

            verify(thermia).setComfortSetpoint(24.0);
            ArgumentCaptor<HeatingManualOverride> saved = ArgumentCaptor.forClass(HeatingManualOverride.class);
            verify(overrides, atLeastOnce()).save(saved.capture());
            assertThat(saved.getAllValues()).noneMatch(o -> o.getSource() == Source.HEAT_PUMP);
        }

        @Test
        @DisplayName("DELETE right after an unnoticed panel change: back to the schedule, no new override")
        void cancelAfterUndetectedPanelChange() {
            HeatingManualOverride open = givenOpenOverride("24.0", Source.API, NOW.plus(Duration.ofHours(3)));
            when(overrides.findOpen()).thenReturn(Optional.of(open)).thenReturn(Optional.empty());
            givenState("24.0", "24.0");
            pumpReads(25.0, 25.0, 21.0);   // panel was set to 25 after the last run
            scheduled("21.0");

            service.cancelOverride();

            verify(thermia).setComfortSetpoint(21.0);
            assertThat(lastSavedOverride()).isSameAs(open);  // only the cancelled one was saved
        }

        @Test
        void cancelWithoutOverride() {
            assertThat(service.cancelOverride()).isFalse();
            verify(thermia, never()).getComfortSetpoint();
        }

        @Test
        @DisplayName("getActiveOverride hides an override whose time is up")
        void activeOverrideHidesExpired() {
            givenOpenOverride("23.0", Source.HEAT_PUMP, NOW.minus(Duration.ofSeconds(1)));

            assertThat(service.getActiveOverride()).isEmpty();
        }

        private HeatingManualOverride apiOverride(String setpoint) {
            return new HeatingManualOverride(new BigDecimal(setpoint), Source.API, NOW, NOW.plus(Duration.ofHours(2)));
        }
    }

    // ------------------------------------------------------------------------

    private void pumpReads(Double first, Double... next) {
        when(thermia.getComfortSetpoint()).thenReturn(first, next);
    }

    private void scheduled(String setpoint) {
        when(schedule.scheduledSetpointAt(NOW)).thenReturn(new BigDecimal(setpoint));
    }

    private void givenState(String desired, String readBack) {
        HeatingControlState s = new HeatingControlState();
        s.setLastDesiredSetpoint(new BigDecimal(desired));
        s.setLastReadBackSetpoint(new BigDecimal(readBack));
        s.setUpdatedAt(NOW.minus(Duration.ofMinutes(15)));
        when(stateRepository.findById(HeatingControlState.SINGLETON_ID)).thenReturn(Optional.of(s));
    }

    private HeatingManualOverride givenOpenOverride(String setpoint, Source source, Instant validUntil) {
        HeatingManualOverride o = new HeatingManualOverride(new BigDecimal(setpoint), source,
                NOW.minus(Duration.ofHours(2)), validUntil);
        when(overrides.findOpen()).thenReturn(Optional.of(o));
        return o;
    }

    private void assertLastState(String desired, String readBack) {
        ArgumentCaptor<HeatingControlState> captor = ArgumentCaptor.forClass(HeatingControlState.class);
        verify(stateRepository, atLeastOnce()).save(captor.capture());
        List<HeatingControlState> saved = captor.getAllValues();
        HeatingControlState last = saved.get(saved.size() - 1);
        assertThat(last.getLastDesiredSetpoint()).isEqualByComparingTo(desired);
        assertThat(last.getLastReadBackSetpoint()).isEqualByComparingTo(readBack);
    }

    private HeatingManualOverride lastSavedOverride() {
        ArgumentCaptor<HeatingManualOverride> captor = ArgumentCaptor.forClass(HeatingManualOverride.class);
        verify(overrides, atLeastOnce()).save(captor.capture());
        List<HeatingManualOverride> saved = captor.getAllValues();
        return saved.get(saved.size() - 1);
    }
}
