package com.ilja.smarthome.energycontrol.thermia.service;

import com.ilja.smarthome.energycontrol.exception.InvalidValueException;
import com.ilja.smarthome.energycontrol.thermia.client.ThermiaModbusClient;
import com.ilja.smarthome.energycontrol.thermia.config.ThermiaProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Write guards of {@link ThermiaHeatPumpService}: out-of-range values must be rejected
 * before anything is sent to the heat pump, valid values are written with the right scaling.
 */
@ExtendWith(MockitoExtension.class)
class ThermiaHeatPumpServiceTest {

    // Holding registers (1-based), see ThermiaHeatPumpService
    private static final int REG_OPERATION_MODE = 1;
    private static final int REG_COMFORT_SETPOINT = 6;
    private static final int REG_HEAT_CURVE_Y_START = 7;
    private static final int REG_TAP_WATER_START = 23;
    private static final int REG_TAP_WATER_STOP = 24;

    @Mock private ThermiaModbusClient modbusClient;

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-31T14:00:00Z"));
    /** How deep we are inside modbusClient.exclusive(...) right now; reads/writes record if they ran outside. */
    private final AtomicInteger exclusiveDepth = new AtomicInteger();
    private boolean deviceUsedOutsideExclusive;

    private ThermiaHeatPumpService service;

    @BeforeEach
    void setUp() {
        service = new ThermiaHeatPumpService(modbusClient, properties(60_000), clock);
        lenient().when(modbusClient.exclusive(any())).thenAnswer(inv -> {
            exclusiveDepth.incrementAndGet();
            try {
                return ((Supplier<?>) inv.getArgument(0)).get();
            } finally {
                exclusiveDepth.decrementAndGet();
            }
        });
    }

    @Nested
    @DisplayName("setComfortSetpoint")
    class ComfortSetpoint {

        @ParameterizedTest(name = "{0} °C is written as {0} x 100")
        @ValueSource(doubles = {15.0, 21.5, 30.0})
        void writesValidSetpointScaledBy100(double celsius) {
            service.setComfortSetpoint(celsius);

            verify(modbusClient).writeHoldingRegister(REG_COMFORT_SETPOINT, (int) Math.round(celsius * 100));
        }

        @Test
        @DisplayName("reads the comfort setpoint from holding register 6, /100")
        void readsSetpoint() {
            when(modbusClient.readHoldingRegisters(REG_COMFORT_SETPOINT, 1)).thenReturn(new int[]{2150});

            assertThat(service.getComfortSetpoint()).isEqualTo(21.5);
        }

        @ParameterizedTest(name = "{0} °C is rejected")
        @ValueSource(doubles = {14.99, 30.01, 0.0, -5.0, 50.0, Double.NaN, Double.POSITIVE_INFINITY})
        void rejectsOutOfRangeWithoutTouchingThePump(double celsius) {
            assertThatThrownBy(() -> service.setComfortSetpoint(celsius))
                    .isInstanceOf(InvalidValueException.class)
                    .hasMessageContaining("Comfort setpoint");

            verifyNoInteractions(modbusClient);
        }
    }

    @Nested
    @DisplayName("setOperationMode")
    class OperationMode {

        @ParameterizedTest(name = "mode {0} is written")
        @ValueSource(ints = {1, 2, 3})
        void writesValidMode(int mode) {
            service.setOperationMode(mode);

            verify(modbusClient).writeHoldingRegister(REG_OPERATION_MODE, mode);
        }

        @ParameterizedTest(name = "mode {0} is rejected")
        @ValueSource(ints = {0, 4, -1, 100})
        void rejectsUnknownMode(int mode) {
            assertThatThrownBy(() -> service.setOperationMode(mode)).isInstanceOf(InvalidValueException.class);

            verifyNoInteractions(modbusClient);
        }
    }

    @Nested
    @DisplayName("setTapWaterTemperatures")
    class TapWater {

        @Test
        void writesStartAndStop() {
            service.setTapWaterTemperatures(45.0, 52.5);

            InOrder order = inOrder(modbusClient);
            order.verify(modbusClient).writeHoldingRegister(REG_TAP_WATER_START, 4500);
            order.verify(modbusClient).writeHoldingRegister(REG_TAP_WATER_STOP, 5250);
        }

        @Test
        void rejectsStartAboveRange() {
            assertThatThrownBy(() -> service.setTapWaterTemperatures(61.0, 65.0))
                    .isInstanceOf(InvalidValueException.class);
            verifyNoInteractions(modbusClient);
        }

        @Test
        void rejectsStopAboveRange() {
            assertThatThrownBy(() -> service.setTapWaterTemperatures(45.0, 66.0))
                    .isInstanceOf(InvalidValueException.class);
            verifyNoInteractions(modbusClient);
        }

        @Test
        @DisplayName("start must be below stop, otherwise DHW would short-cycle")
        void rejectsStartNotBelowStop() {
            assertThatThrownBy(() -> service.setTapWaterTemperatures(50.0, 50.0))
                    .isInstanceOf(InvalidValueException.class)
                    .hasMessageContaining("below stop");
            verifyNoInteractions(modbusClient);
        }
    }

    @Nested
    @DisplayName("setHeatCurveY")
    class HeatCurve {

        @Test
        void writesAllSevenPoints() {
            service.setHeatCurveY(new double[]{20, 25, 30, 35, 40, 45, 50});

            for (int i = 0; i < 7; i++) {
                verify(modbusClient).writeHoldingRegister(REG_HEAT_CURVE_Y_START + i, (20 + 5 * i) * 100);
            }
        }

        @Test
        void rejectsWrongNumberOfPoints() {
            assertThatThrownBy(() -> service.setHeatCurveY(new double[]{20, 25, 30}))
                    .isInstanceOf(InvalidValueException.class)
                    .hasMessageContaining("exactly 7");
            assertThatThrownBy(() -> service.setHeatCurveY(null))
                    .isInstanceOf(InvalidValueException.class);
            verifyNoInteractions(modbusClient);
        }

        @Test
        @DisplayName("one bad point rejects the whole curve — nothing is written half-way")
        void rejectsWholeCurveIfOnePointIsOutOfRange() {
            assertThatThrownBy(() -> service.setHeatCurveY(new double[]{20, 25, 30, 35, 40, 45, 70}))
                    .isInstanceOf(InvalidValueException.class)
                    .hasMessageContaining("point 7");
            verifyNoInteractions(modbusClient);
        }
    }

    @Nested
    @DisplayName("snapshot served to the UI (GET /thermia/data)")
    class Snapshot {

        @BeforeEach
        void pumpAnswersReads() {
            lenient().when(modbusClient.readInputRegisters(anyInt(), anyInt())).thenAnswer(inv -> track(new int[(Integer) inv.getArgument(1)]));
            lenient().when(modbusClient.readHoldingRegisters(anyInt(), anyInt())).thenAnswer(inv -> track(new int[(Integer) inv.getArgument(1)]));
            lenient().when(modbusClient.readDiscreteInputs(anyInt(), anyInt())).thenAnswer(inv -> track(new boolean[(Integer) inv.getArgument(1)]));
            lenient().when(modbusClient.readCoils(anyInt(), anyInt())).thenAnswer(inv -> track(new boolean[(Integer) inv.getArgument(1)]));
        }

        @Test
        @DisplayName("a second request within the max age is served without touching the heat pump")
        void servedFromSnapshotWhileFresh() {
            service.getRecentHeatPumpData();
            clock.advance(Duration.ofSeconds(59));
            service.getRecentHeatPumpData();

            assertThat(liveReads()).isEqualTo(1);
        }

        @Test
        void staleSnapshotIsReadLive() {
            service.getRecentHeatPumpData();
            clock.advance(Duration.ofSeconds(61));
            service.getRecentHeatPumpData();

            assertThat(liveReads()).isEqualTo(2);
        }

        @Test
        @DisplayName("the data collection's live read refreshes the snapshot")
        void liveReadRefreshesSnapshot() {
            service.getHeatPumpData();
            service.getRecentHeatPumpData();

            assertThat(liveReads()).isEqualTo(1);
        }

        @Test
        @DisplayName("any write discards the snapshot, so the UI sees its own change")
        void writeInvalidates() {
            service.getRecentHeatPumpData();
            service.setComfortSetpoint(21.0);
            service.getRecentHeatPumpData();

            assertThat(liveReads()).isEqualTo(2);
        }

        @Test
        @DisplayName("a read that overlapped a write is not cached")
        void readOverlappingWriteIsNotCached() {
            when(modbusClient.readCoils(anyInt(), anyInt())).thenAnswer(inv -> {
                service.enableHeating(); // a write lands while the status read is in progress
                return new boolean[(Integer) inv.getArgument(1)];
            }).thenAnswer(inv -> new boolean[(Integer) inv.getArgument(1)]);

            service.getRecentHeatPumpData();
            service.getRecentHeatPumpData();

            assertThat(liveReads()).isEqualTo(2);
        }

        @Test
        @DisplayName("max age 0 disables the snapshot")
        void zeroMaxAgeAlwaysLive() {
            ThermiaHeatPumpService alwaysLive = new ThermiaHeatPumpService(modbusClient, properties(0), clock);

            alwaysLive.getRecentHeatPumpData();
            alwaysLive.getRecentHeatPumpData();

            assertThat(liveReads()).isEqualTo(2);
        }

        @Test
        @DisplayName("the five status batches are read inside one exclusive session")
        void statusIsOneExclusiveSession() {
            service.getStatus();

            verify(modbusClient, times(1)).exclusive(any());
            assertThat(deviceUsedOutsideExclusive).isFalse();
        }

        @Test
        @DisplayName("writes run under the device lock, so no read can publish between write and invalidation")
        void writesRunUnderTheDeviceLock() {
            doAnswer(inv -> track(null)).when(modbusClient).writeHoldingRegister(anyInt(), anyInt());
            doAnswer(inv -> track(null)).when(modbusClient).writeCoil(anyInt(), anyBoolean());

            service.setComfortSetpoint(21.0);
            service.enableHeating();

            verify(modbusClient).writeHoldingRegister(6, 2100);
            assertThat(deviceUsedOutsideExclusive).isFalse();
        }

        @Test
        @DisplayName("a snapshot from the future (wall clock stepped back) is treated as stale")
        void clockSteppedBack() {
            service.getRecentHeatPumpData();
            clock.advance(Duration.ofMinutes(-10));
            service.getRecentHeatPumpData();

            assertThat(liveReads()).isEqualTo(2);
        }

        private <T> T track(T result) {
            if (exclusiveDepth.get() == 0) {
                deviceUsedOutsideExclusive = true;
            }
            return result;
        }

        /** Each full status read reads holding registers 1–24 exactly once. */
        private int liveReads() {
            return (int) org.mockito.Mockito.mockingDetails(modbusClient).getInvocations().stream()
                    .filter(i -> i.getMethod().getName().equals("readHoldingRegisters")
                            && i.getArgument(0).equals(1) && i.getArgument(1).equals(24))
                    .count();
        }
    }

    private static ThermiaProperties properties(long snapshotMaxAgeMs) {
        ThermiaProperties props = new ThermiaProperties();
        props.setSnapshotMaxAgeMs(snapshotMaxAgeMs);
        return props;
    }

    /** Fixed clock that tests can move forward. */
    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
