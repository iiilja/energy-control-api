package com.ilja.smarthome.energycontrol.sungrow.service;

import com.ilja.smarthome.energycontrol.exception.InvalidValueException;
import com.ilja.smarthome.energycontrol.modbus.ModbusTcpDevice;
import com.ilja.smarthome.energycontrol.sungrow.client.SungrowModbusClient;
import com.ilja.smarthome.energycontrol.sungrow.config.SungrowProperties;
import com.ilja.smarthome.energycontrol.sungrow.dto.SungrowStatusResponse;
import com.ilja.smarthome.energycontrol.sungrow.exception.SungrowCommunicationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * High-level service for the Sungrow SG15RT inverter.
 *
 * Register map (1-based):
 *   Holding registers FC03/FC06:
 *     5006 – Start/Stop:           0xCF = run,  0xCE = stop
 *     5007 – Power limit switch:   0xAA = on,   0x55 = off
 *     5008 – Power limit %:        unit = 0.1%, e.g. 133 → 13.3%
 *     5039 – Power limit kW:       unit = 0.1 kW, e.g. 20 → 2.0 kW
 *   Input registers FC04 (read-only):
 *     5003 – Daily energy yield:   U16, unit = 0.1 kWh
 *     5004 – Total energy:         U16, unit = kWh
 *     5031 – Active power:         U16, unit = W
 */
@Slf4j
@Service
public class SungrowInverterService {

    // Register addresses (1-based, as in Sungrow documentation)
    // Holding registers (FC03/FC06)
    private static final int REG_START_STOP          = 5006;
    private static final int REG_POWER_LIMIT_SWITCH  = 5007;
    private static final int REG_POWER_LIMIT_PERCENT = 5008;
    private static final int REG_POWER_LIMIT_KW      = 5039;
    // Input registers (FC04, read-only)
    private static final int REG_DAILY_ENERGY        = 5003;  // U16, unit 0.1 kWh
    private static final int REG_TOTAL_ENERGY        = 5004;  // U16, unit kWh
    private static final int REG_ACTIVE_POWER        = 5031;  // U16, unit W

    // Magic values
    private static final int CMD_RUN             = 0xCF;
    private static final int CMD_STOP            = 0xCE;
    private static final int CMD_LIMIT_ON        = 0xAA;
    private static final int CMD_LIMIT_OFF       = 0x55;

    private final SungrowModbusClient modbusClient;
    private final Clock clock;
    private final long snapshotMaxAgeMs;

    /** Last status read, served to the UI while fresh. Null after any write. */
    private volatile Snapshot latest;
    /** Bumped by every write, so a read that overlapped a write is not cached. */
    private final AtomicLong writeGeneration = new AtomicLong();

    private record Snapshot(SungrowStatusResponse status, Instant readAt) {}

    public SungrowInverterService(SungrowModbusClient modbusClient, SungrowProperties props, Clock clock) {
        this.modbusClient = modbusClient;
        this.clock = clock;
        this.snapshotMaxAgeMs = props.getSnapshotMaxAgeMs();
    }

    // -------------------------------------------------------------------------
    // Read

    /**
     * Read and return a complete status snapshot from the inverter.
     */
    public SungrowStatusResponse getStatus() {
        // Read and publish under the device lock, so no write can slip in between.
        return modbusClient.exclusive(() -> {
            long generation = writeGeneration.get();
            SungrowStatusResponse status = readStatus();
            if (writeGeneration.get() == generation) {
                latest = new Snapshot(status, Instant.now(clock));
            }
            return status;
        });
    }

    /**
     * Status for the UI: the last read if it is at most {@code sungrow.snapshot-max-age-ms} old, otherwise a live
     * read. Concurrent callers share one live read.
     */
    public SungrowStatusResponse getRecentStatus() {
        Snapshot fresh = freshSnapshot();
        if (fresh != null) {
            return fresh.status();
        }
        return modbusClient.exclusive(() -> {
            Snapshot s = freshSnapshot();
            return s != null ? s.status() : getStatus();
        });
    }

    private SungrowStatusResponse readStatus() {
        // Read registers 5006–5008 in a single request (3 consecutive registers),
        // then read 5039 separately as it is not adjacent.
        int[] first = modbusClient.readHoldingRegisters(REG_START_STOP, 3);
        int limitKwRaw = modbusClient.readHoldingRegister(REG_POWER_LIMIT_KW);

        boolean running           = (first[0] == CMD_RUN);
        boolean powerLimitEnabled = (first[1] == CMD_LIMIT_ON);
        double  powerLimitPercent = first[2] / 10.0;
        double  powerLimitKw      = limitKwRaw / 10.0;

        // FC04 input registers (read-only telemetry)
        int   powerRaw   = modbusClient.readInputRegister(REG_ACTIVE_POWER);
        int   dailyRaw   = modbusClient.readInputRegister(REG_DAILY_ENERGY);
        int   totalRaw   = modbusClient.readInputRegister(REG_TOTAL_ENERGY);

        double activePowerW    = powerRaw;
        double dailyEnergyKwh  = dailyRaw / 10.0;
        double totalEnergyKwh  = totalRaw;

        log.debug("Inverter status: running={}, limitEnabled={}, limit={}% / {} kW, power={} W, daily={} kWh, total={} kWh",
                running, powerLimitEnabled, powerLimitPercent, powerLimitKw,
                activePowerW, dailyEnergyKwh, totalEnergyKwh);

        return new SungrowStatusResponse(running, powerLimitEnabled, powerLimitPercent, powerLimitKw,
                activePowerW, dailyEnergyKwh, totalEnergyKwh);
    }

    // -------------------------------------------------------------------------
    // Power limit control

    /**
     * Enable limit if not already on, then write the limit value.
     * The inverter requires the switch to be enabled before accepting a limit value.
     */
    public void applyPowerLimit(Double kw, Double percent) {
        if (!isPowerLimitEnabled()) {
            enablePowerLimit();
        }
        if (kw != null) {
            setPowerLimitKw(kw);
        } else {
            setPowerLimitPercent(percent);
        }
    }

    /**
     * Set the power feed-in limit as a percentage of rated power.
     *
     * @param percent 0.0–110.0 (register unit is 0.1%, so 13.3% → write 133)
     */
    public void setPowerLimitPercent(double percent) {
        if (percent < 0 || percent > 110) {
            throw new InvalidValueException("Power limit percent must be 0–110, got: " + percent);
        }
        int raw = (int) Math.round(percent * 10);
        log.info("Setting power limit to {}% (raw {})", percent, raw);
        writeRegister(REG_POWER_LIMIT_PERCENT, raw);
    }

    /**
     * Set the power feed-in limit in kilowatts.
     *
     * @param kw target power limit (register unit is 0.1 kW, so 2.0 kW → write 20)
     */
    public void setPowerLimitKw(double kw) {
        if (kw < 0) {
            throw new InvalidValueException("Power limit kW must be ≥ 0, got: " + kw);
        }
        int raw = (int) Math.round(kw * 10);
        log.info("Setting power limit to {} kW (raw {})", kw, raw);
        writeRegister(REG_POWER_LIMIT_KW, raw);
    }

    public boolean isPowerLimitEnabled() {
        return modbusClient.readHoldingRegister(REG_POWER_LIMIT_SWITCH) == CMD_LIMIT_ON;
    }

    public double getPowerLimitKw() {
        return modbusClient.readHoldingRegister(REG_POWER_LIMIT_KW) / 10.0;
    }

    /**
     * Enable the power feed-in limitation (register 5007 → 0xAA).
     */
    public void enablePowerLimit() {
        log.info("Enabling power limit");
        writeRegister(REG_POWER_LIMIT_SWITCH, CMD_LIMIT_ON);
    }

    /**
     * Disable the power feed-in limitation (register 5007 → 0x55).
     */
    public void disablePowerLimit() {
        log.info("Disabling power limit");
        writeRegister(REG_POWER_LIMIT_SWITCH, CMD_LIMIT_OFF);
    }

    // -------------------------------------------------------------------------
    // Start / Stop

    /**
     * Send the Run command to the inverter (register 5006 → 0xCF).
     */
    public void startInverter() {
        log.info("Sending START command to inverter");
        writeRegister(REG_START_STOP, CMD_RUN);
    }

    /**
     * Send the Stop command to the inverter (register 5006 → 0xCE).
     */
    public void stopInverter() {
        log.warn("Sending STOP command to inverter");
        writeRegister(REG_START_STOP, CMD_STOP);
    }

    // -------------------------------------------------------------------------
    // Snapshot

    private Snapshot freshSnapshot() {
        Snapshot s = latest;
        if (s == null || snapshotMaxAgeMs <= 0) {
            return null;
        }
        long ageMs = Duration.between(s.readAt(), Instant.now(clock)).toMillis();
        return ageMs >= 0 && ageMs <= snapshotMaxAgeMs ? s : null; // negative: the clock stepped back
    }

    /**
     * Every write goes through here, so the UI never gets a snapshot from before its own change.
     * Write and invalidation happen under the device lock, so no read can publish in between.
     */
    private void writeRegister(int register, int value) {
        modbusClient.exclusive(() -> {
            try {
                modbusClient.writeHoldingRegister(register, value);
            } finally {
                writeGeneration.incrementAndGet();
                latest = null;
            }
            return null;
        });
    }

    // -------------------------------------------------------------------------
    // Connectivity check

    /**
     * Check whether the inverter is reachable via Modbus TCP.
     *
     * @return true if a register read succeeded
     */
    public boolean testConnection() {
        try {
            modbusClient.readHoldingRegister(REG_START_STOP);
            return true;
        } catch (SungrowCommunicationException e) {
            if (ModbusTcpDevice.isBusy(e)) {
                // Another transaction kept the inverter busy: it is answering someone, so count it as reachable.
                log.info("Inverter connection test: inverter busy with another request");
                return true;
            }
            log.warn("Inverter connection test failed: {}", e.getMessage());
            return false;
        }
    }
}
