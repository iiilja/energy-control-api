package com.ilja.smarthome.energycontrol.sungrow.service;

import com.ilja.smarthome.energycontrol.sungrow.client.SungrowModbusClient;
import com.ilja.smarthome.energycontrol.sungrow.config.SungrowProperties;
import com.ilja.smarthome.energycontrol.sungrow.dto.SungrowStatusResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** {@link SungrowInverterService}: status snapshot served to the UI, one exclusive read session, writes invalidate. */
@ExtendWith(MockitoExtension.class)
class SungrowInverterServiceTest {

    private static final int REG_START_STOP = 5006;

    @Mock private SungrowModbusClient modbusClient;

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-31T14:00:00Z"));
    private SungrowInverterService service;
    private final AtomicInteger exclusiveDepth = new AtomicInteger();
    private boolean deviceUsedOutsideExclusive;

    @BeforeEach
    void setUp() {
        SungrowProperties props = new SungrowProperties();
        props.setSnapshotMaxAgeMs(30_000);
        service = new SungrowInverterService(modbusClient, props, clock);

        lenient().when(modbusClient.exclusive(any())).thenAnswer(inv -> {
            exclusiveDepth.incrementAndGet();
            try {
                return ((Supplier<?>) inv.getArgument(0)).get();
            } finally {
                exclusiveDepth.decrementAndGet();
            }
        });
        lenient().when(modbusClient.readHoldingRegisters(anyInt(), anyInt())).thenAnswer(inv -> track(new int[]{0xCF, 0xAA, 133}));
        lenient().when(modbusClient.readHoldingRegister(anyInt())).thenAnswer(inv -> track(30));
        lenient().when(modbusClient.readInputRegister(anyInt())).thenAnswer(inv -> track(1500));
    }

    @Test
    void statusIsDecoded() {
        SungrowStatusResponse status = service.getRecentStatus();

        assertThat(status.running()).isTrue();
        assertThat(status.powerLimitEnabled()).isTrue();
        assertThat(status.powerLimitPercent()).isEqualTo(13.3);
        assertThat(status.powerLimitKw()).isEqualTo(3.0);
    }

    @Test
    @DisplayName("within the max age the UI gets the snapshot, after it a live read")
    void snapshotWhileFresh() {
        service.getRecentStatus();
        clock.advance(Duration.ofSeconds(29));
        service.getRecentStatus();
        verify(modbusClient, times(1)).readHoldingRegisters(REG_START_STOP, 3);

        clock.advance(Duration.ofSeconds(2));
        service.getRecentStatus();
        verify(modbusClient, times(2)).readHoldingRegisters(REG_START_STOP, 3);
    }

    @Test
    @DisplayName("a power-limit change discards the snapshot, so the UI sees it right away")
    void writeInvalidates() {
        service.getRecentStatus();
        service.enablePowerLimit();
        service.getRecentStatus();

        verify(modbusClient, times(2)).readHoldingRegisters(REG_START_STOP, 3);
    }

    @Test
    @DisplayName("the status registers are read inside one exclusive session")
    void oneExclusiveSession() {
        service.getStatus();

        verify(modbusClient, times(1)).exclusive(any());
        assertThat(deviceUsedOutsideExclusive).isFalse();
    }

    @Test
    @DisplayName("writes run under the device lock")
    void writesRunUnderTheDeviceLock() {
        doAnswer(inv -> track(null)).when(modbusClient).writeHoldingRegister(anyInt(), anyInt());

        service.enablePowerLimit();
        service.setPowerLimitKw(3.0);

        assertThat(deviceUsedOutsideExclusive).isFalse();
    }

    private <T> T track(T result) {
        if (exclusiveDepth.get() == 0) {
            deviceUsedOutsideExclusive = true;
        }
        return result;
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
