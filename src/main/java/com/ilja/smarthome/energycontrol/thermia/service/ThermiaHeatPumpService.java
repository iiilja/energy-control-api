package com.ilja.smarthome.energycontrol.thermia.service;

import com.ilja.smarthome.energycontrol.thermia.client.ThermiaModbusClient;
import com.ilja.smarthome.energycontrol.thermia.dto.HeatPumpDataResponse;
import com.ilja.smarthome.energycontrol.thermia.dto.ThermiaStatusResponse;
import com.ilja.smarthome.energycontrol.thermia.dto.ThermiaTemperaturesResponse;
import com.ilja.smarthome.energycontrol.thermia.exception.ThermiaCommException;

import java.util.Arrays;
import java.util.stream.Collectors;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * High-level service for the Thermia heat pump.
 *
 * Register map (1-based for input/holding registers):
 *   Input registers FC04 (read-only):
 *     2  – Current demand:        1-7, 98-100
 *     6  – Compressor RPM:        0-3000
 *     8  – Discharge temp:        signed int16 / 100 → °C
 *     9  – Condenser inlet temp:  signed int16 / 100 → °C
 *     10 – Condenser outlet temp: signed int16 / 100 → °C
 *     11 – Brine inlet temp:      signed int16 / 100 → °C
 *     12 – Brine outlet temp:     signed int16 / 100 → °C
 *     13 – System supply line:    signed int16 / 100 → °C
 *     14 – Outdoor temp:          signed int16 / 100 → °C
 *     16 – DHW top temp:          signed int16 / 100 → °C
 *     17 – DHW bottom temp:       signed int16 / 100 → °C
 *     19 – Supply setpoint:       signed int16 / 100 → °C
 *     21-27 – Heat curve X[7]:   signed int16 / 100 → °C (outdoor temp points)
 *     49-50 – Compressor hours:  32-bit (MSB reg49, LSB reg50)
 *     51-52 – Heating hours:     32-bit
 *     53-54 – Ext heater hours:  32-bit
 *     55 – Compressor speed %:   uint16 / 100 → %
 *   Holding registers FC03/FC06:
 *     1  – Operation mode:        1=OFF, 2=Standby, 3=ON/Auto
 *     6  – Comfort wheel:         int16 / 100 → °C, range 15-30
 *     7-13 – Heat curve Y[7]:    int16 / 100 → °C, range 15-65
 *     23 – DHW start temp:        int16 / 100 → °C, range 30-60
 *     24 – DHW stop temp:         int16 / 100 → °C, range 30-65
 *   Discrete inputs FC02 (0-based):
 *     0 – Class A alarm, 1 – Class B alarm, 2 – Class C alarm
 *   Coils FC01/FC05 (0-based):
 *     8 – DHW enabled, 9 – Heating enabled, 10 – Cooling enabled
 */
@Slf4j
@Service
public class ThermiaHeatPumpService {

    // Input registers (FC04) — 1-based
    private static final int REG_IN_CURRENT_DEMAND     = 2;
    private static final int REG_IN_COMPRESSOR_RPM     = 6;
    private static final int REG_IN_DISCHARGE_TEMP     = 8;
    private static final int REG_IN_TEMP_BATCH_START   = 8;   // batch 8–19 for temperatures
    private static final int REG_IN_TEMP_BATCH_COUNT   = 12;
    private static final int REG_IN_STATUS_BATCH_START = 1;   // batch 1–27 for full status pass 1
    private static final int REG_IN_STATUS_BATCH_COUNT = 27;
    private static final int REG_IN_HOURS_BATCH_START       = 49;  // batch 49–68 for hours, speed, heater step
    private static final int REG_IN_INTERNAL_HEATER_STEP    = 68;  // de-facto 30068
    private static final int REG_IN_HOURS_BATCH_COUNT       = REG_IN_INTERNAL_HEATER_STEP - REG_IN_HOURS_BATCH_START + 1; // 20

    // Holding registers (FC03/FC06) — 1-based
    private static final int REG_OPERATION_MODE        = 1;
    private static final int REG_COMFORT_SETPOINT      = 6;
    private static final int REG_HEAT_CURVE_Y_START    = 7;
    private static final int REG_TAP_WATER_START_TEMP        = 23;
    private static final int REG_TAP_WATER_STOP_TEMP         = 24;
    private static final int REG_HOLDING_BATCH_START   = 1;   // batch 1–24 covers mode, setpoint, curve Y, DHW
    private static final int REG_HOLDING_BATCH_COUNT   = 24;

    // Coils (FC01/FC05) — 0-based (de-facto = address + 1)
    private static final int COIL_INTERNAL_HEATER      = 4;   // de-facto 5
    private static final int COIL_TAP_WATER_ENABLED          = 8;   // de-facto 9
    private static final int COIL_HEATING_ENABLED      = 9;   // de-facto 10
    private static final int COIL_COOLING_ENABLED      = 10;  // de-facto 11
    private static final int COIL_HOT_GAS_PUMP         = 14;  // de-facto 15
    private static final int COIL_ANTI_LEGIONELLA      = 24;  // de-facto 25
    // Single batch covers all coils above: read from COIL_INTERNAL_HEATER to COIL_ANTI_LEGIONELLA inclusive
    private static final int COIL_BATCH_START          = COIL_INTERNAL_HEATER;
    private static final int COIL_BATCH_COUNT          = COIL_ANTI_LEGIONELLA - COIL_BATCH_START + 1; // 21

    // Discrete inputs (FC02) — 0-based
    private static final int DISCRETE_ALARM_A          = 0;

    // Millis between Modbus read batches (matches Arduino MODBUS_TRANSACTION_DELAY)
    private static final int BATCH_DELAY_MS = 50;

    private final ThermiaModbusClient modbusClient;

    public ThermiaHeatPumpService(ThermiaModbusClient modbusClient) {
        this.modbusClient = modbusClient;
    }

    // -------------------------------------------------------------------------
    // Read

    /**
     * Read and return a complete status snapshot from the heat pump.
     * Uses four read batches with 50 ms gaps (same pattern as the Arduino firmware).
     */
    public ThermiaStatusResponse getStatus() {
        // Batch 1: input registers 1–27 (demand, RPM, temperatures, heat curve X)
        int[] inputLow = modbusClient.readInputRegisters(REG_IN_STATUS_BATCH_START, REG_IN_STATUS_BATCH_COUNT);
        sleep(BATCH_DELAY_MS);

        // Batch 2: input registers 49–55 (running hours, compressor speed %)
        int[] inputHigh = modbusClient.readInputRegisters(REG_IN_HOURS_BATCH_START, REG_IN_HOURS_BATCH_COUNT);
        sleep(BATCH_DELAY_MS);

        // Batch 3: holding registers 1–24 (mode, setpoint, heat curve Y, DHW temps)
        int[] holding = modbusClient.readHoldingRegisters(REG_HOLDING_BATCH_START, REG_HOLDING_BATCH_COUNT);
        sleep(BATCH_DELAY_MS);

        // Batch 4: alarm flags (FC02) and function enable states (FC01)
        boolean[] alarms = modbusClient.readDiscreteInputs(DISCRETE_ALARM_A, 3);
        boolean[] coils  = modbusClient.readCoils(COIL_BATCH_START, COIL_BATCH_COUNT);

        // --- Extract values from input batch 1 (index = register - 1) ---
        int currentDemand  = inputLow[REG_IN_CURRENT_DEMAND - 1];
        int compressorRpm  = inputLow[REG_IN_COMPRESSOR_RPM - 1];

        ThermiaTemperaturesResponse temps = extractTemperatures(inputLow);

        double[] heatCurveX = new double[7];
        for (int i = 0; i < 7; i++) {
            heatCurveX[i] = toSigned(inputLow[20 + i]) / 100.0;  // regs 21–27, indices 20–26
        }

        // --- Extract values from input batch 2 (base offset = reg 49, index 0) ---
        long compressorHours    = to32bit(inputHigh[0], inputHigh[1]);
        long heatingHours       = to32bit(inputHigh[2], inputHigh[3]);
        long externalHeaterHours= to32bit(inputHigh[4], inputHigh[5]);
        double compressorSpeed  = inputHigh[6] / 100.0;
        int internalHeaterStep  = inputHigh[REG_IN_INTERNAL_HEATER_STEP - REG_IN_HOURS_BATCH_START]; // reg 68

        // --- Extract values from holding batch (index = register - 1) ---
        int  operationMode    = holding[REG_OPERATION_MODE - 1];
        double heatingSetpoint = toSigned(holding[REG_COMFORT_SETPOINT - 1]) / 100.0;

        double[] heatCurveY = new double[7];
        for (int i = 0; i < 7; i++) {
            heatCurveY[i] = toSigned(holding[REG_HEAT_CURVE_Y_START - 1 + i]) / 100.0;
        }

        log.debug("Thermia status: mode={}, demand={}, rpm={}, setpoint={}, alarms={}/{}/{}",
                operationMode, currentDemand, compressorRpm, heatingSetpoint,
                alarms[0], alarms[1], alarms[2]);

        return new ThermiaStatusResponse(
                temps,
                operationMode,
                currentDemand,
                compressorRpm,
                compressorSpeed,
                compressorHours,
                heatingHours,
                externalHeaterHours,
                alarms[0],
                alarms[1],
                alarms[2],
                heatingSetpoint,
                heatCurveX,
                heatCurveY,
                coils[COIL_TAP_WATER_ENABLED       - COIL_BATCH_START],
                coils[COIL_HEATING_ENABLED   - COIL_BATCH_START],
                coils[COIL_COOLING_ENABLED   - COIL_BATCH_START],
                coils[COIL_HOT_GAS_PUMP      - COIL_BATCH_START],
                coils[COIL_ANTI_LEGIONELLA   - COIL_BATCH_START],
                coils[COIL_INTERNAL_HEATER   - COIL_BATCH_START],
                internalHeaterStep
        );
    }

    /**
     * Read and return only the temperature sensors.
     * Single batch read of input registers 8–19.
     */
    public ThermiaTemperaturesResponse getTemperatures() {
        int[] raw = modbusClient.readInputRegisters(REG_IN_TEMP_BATCH_START, REG_IN_TEMP_BATCH_COUNT);
        return extractTemperatures(raw, 0);
    }

    // -------------------------------------------------------------------------
    // Write — mode and setpoints

    /** Set heat pump operation mode: 1=OFF, 2=Standby, 3=ON/Auto. */
    public void setOperationMode(int mode) {
        log.info("Setting operation mode to {}", mode);
        modbusClient.writeHoldingRegister(REG_OPERATION_MODE, mode);
    }

    /** Set comfort wheel (desired indoor temperature), 15–30 °C. */
    public void setComfortSetpoint(double celsius) {
        int raw = (int) Math.round(celsius * 100);
        log.info("Setting comfort setpoint to {} °C (raw {})", celsius, raw);
        modbusClient.writeHoldingRegister(REG_COMFORT_SETPOINT, raw);
    }

    /** Set tap water start and stop temperatures. */
    public void setTapWaterTemperatures(double startCelsius, double stopCelsius) {
        log.info("Setting tap water temperatures: start={} °C, stop={} °C", startCelsius, stopCelsius);
        modbusClient.writeHoldingRegister(REG_TAP_WATER_START_TEMP, (int) Math.round(startCelsius * 100));
        modbusClient.writeHoldingRegister(REG_TAP_WATER_STOP_TEMP,  (int) Math.round(stopCelsius  * 100));
    }

    /** Set all 7 heat curve Y-axis (supply temperature) points, 15–65 °C each. */
    public void setHeatCurveY(double[] points) {
        log.info("Setting heat curve Y points: {}", points);
        for (int i = 0; i < 7; i++) {
            modbusClient.writeHoldingRegister(REG_HEAT_CURVE_Y_START + i, (int) Math.round(points[i] * 100));
        }
    }

    // -------------------------------------------------------------------------
    // Write — coil enables

    public void enableTapWater() {
        log.info("Enabling tap water");
        modbusClient.writeCoil(COIL_TAP_WATER_ENABLED, true);
    }

    public void disableTapWater() {
        log.info("Disabling tap water");
        modbusClient.writeCoil(COIL_TAP_WATER_ENABLED, false);
    }

    public void enableHeating() {
        log.info("Enabling heating");
        modbusClient.writeCoil(COIL_HEATING_ENABLED, true);
    }

    public void disableHeating() {
        log.info("Disabling heating");
        modbusClient.writeCoil(COIL_HEATING_ENABLED, false);
    }

    public void enableCooling() {
        log.info("Enabling active cooling");
        modbusClient.writeCoil(COIL_COOLING_ENABLED, true);
    }

    public void disableCooling() {
        log.info("Disabling active cooling");
        modbusClient.writeCoil(COIL_COOLING_ENABLED, false);
    }

    public void enableHotGasPump() {
        log.info("Enabling hot gas pump");
        modbusClient.writeCoil(COIL_HOT_GAS_PUMP, true);
    }

    public void disableHotGasPump() {
        log.info("Disabling hot gas pump");
        modbusClient.writeCoil(COIL_HOT_GAS_PUMP, false);
    }

    public void enableAntiLegionella() {
        log.info("Enabling anti-legionella cycle");
        modbusClient.writeCoil(COIL_ANTI_LEGIONELLA, true);
    }

    public void disableAntiLegionella() {
        log.info("Disabling anti-legionella cycle");
        modbusClient.writeCoil(COIL_ANTI_LEGIONELLA, false);
    }

    public void enableInternalAdditionalHeater() {
        log.info("Enabling internal additional heater");
        modbusClient.writeCoil(COIL_INTERNAL_HEATER, true);
    }

    public void disableInternalAdditionalHeater() {
        log.info("Disabling internal additional heater");
        modbusClient.writeCoil(COIL_INTERNAL_HEATER, false);
    }

    public HeatPumpDataResponse getHeatPumpData() {
        ThermiaStatusResponse s = getStatus();

        HeatPumpDataResponse.StatusDto statusDto = new HeatPumpDataResponse.StatusDto();
        statusDto.setConnected(true);
        statusDto.setLastUpdate(System.currentTimeMillis());
        statusDto.setOperationMode((short) s.operationMode());
        statusDto.setOperationModeText(operationModeText(s.operationMode()));
        statusDto.setAlarmActive(s.alarmClassA() || s.alarmClassB() || s.alarmClassC());
        statusDto.setCompressorRunning(s.currentDemand() >= 1 && s.currentDemand() <= 7);
        statusDto.setCurrentDemand((short) s.currentDemand());
        statusDto.setCurrentDemandText(currentDemandText(s.currentDemand()));

        HeatPumpDataResponse.TemperaturesDto tempsDto = getTemperaturesDto(s);

        HeatPumpDataResponse.CompressorDto compressorDto = new HeatPumpDataResponse.CompressorDto();
        compressorDto.setRpm(s.compressorRpm());
        compressorDto.setSpeed((int) s.compressorSpeedPercent());
        compressorDto.setHours((int) s.compressorHours());

        HeatPumpDataResponse.HeatingDto heatingDto = new HeatPumpDataResponse.HeatingDto();
        heatingDto.setSetpoint(s.heatingSetpoint());
        heatingDto.setHours((int) s.heatingHours());
        heatingDto.setExternalHeaterHours((int) s.externalHeaterHours());

        HeatPumpDataResponse.HeatCurveDto curveDto = new HeatPumpDataResponse.HeatCurveDto();
        curveDto.setOutdoorTemp(Arrays.stream(s.heatCurveX()).boxed().collect(Collectors.toList()));
        curveDto.setSupplyTemp(Arrays.stream(s.heatCurveY()).boxed().collect(Collectors.toList()));

        HeatPumpDataResponse.InternalHeaterDto internalHeaterDto = new HeatPumpDataResponse.InternalHeaterDto();
        internalHeaterDto.setStep(s.internalHeaterStep());

        HeatPumpDataResponse.EnablesDto enablesDto = new HeatPumpDataResponse.EnablesDto();
        enablesDto.setHeating(s.heatingEnabled());
        enablesDto.setTapWater(s.tapWaterEnabled());
        enablesDto.setCooling(s.coolingEnabled());
        enablesDto.setHotGasPump(s.hotGasPumpEnabled());
        enablesDto.setAntiLegionella(s.antiLegionellaEnabled());
        enablesDto.setInternalHeater(s.internalHeaterEnabled());

        HeatPumpDataResponse result = new HeatPumpDataResponse();
        result.setStatus(statusDto);
        result.setTemperatures(tempsDto);
        result.setCompressor(compressorDto);
        result.setHeating(heatingDto);
        result.setHeatCurve(curveDto);
        result.setInternalHeater(internalHeaterDto);
        result.setEnables(enablesDto);
        return result;
    }

    private static HeatPumpDataResponse.@NonNull TemperaturesDto getTemperaturesDto(ThermiaStatusResponse s) {
        HeatPumpDataResponse.TemperaturesDto tempsDto = new HeatPumpDataResponse.TemperaturesDto();
        ThermiaTemperaturesResponse t = s.temperatures();
        tempsDto.setOutdoor(t.outdoorTemp());
        tempsDto.setBrineIn(t.brineInletTemp());
        tempsDto.setBrineOut(t.brineOutletTemp());
        tempsDto.setSystemSupplyIn(t.condenserInletTemp());
        tempsDto.setSystemSupplyOut(t.condenserOutletTemp());
        tempsDto.setSystemSupplyLine(t.systemSupplyLineTemp());
        tempsDto.setSystemSupplySetpoint(t.systemSupplySetpoint());
        tempsDto.setTapWaterTop(t.tapWaterTopTemp());
        tempsDto.setTapWaterLower(t.tapWaterBottomTemp());
        return tempsDto;
    }

    private static String operationModeText(int mode) {
        return switch (mode) {
            case 1 -> "OFF";
            case 2 -> "Standby";
            case 3 -> "ON/Auto";
            default -> "Unknown";
        };
    }

    private static String currentDemandText(int demand) {
        return switch (demand) {
            case 1  -> "Manual operation";
            case 2  -> "Defrost";
            case 3  -> "DHW heating";
            case 4  -> "Space heating";
            case 5  -> "Cooling";
            case 6  -> "Pool heating";
            case 7  -> "Anti-legionella";
            case 98 -> "Standby";
            case 99 -> "No demand";
            case 100 -> "OFF";
            default -> "Unknown";
        };
    }

    // -------------------------------------------------------------------------
    // Connectivity check

    /** @return true if a Modbus register read succeeds */
    public boolean testConnection() {
        try {
            modbusClient.readInputRegisters(REG_IN_DISCHARGE_TEMP, 1);
            return true;
        } catch (ThermiaCommException e) {
            log.warn("Thermia connection test failed: {}", e.getMessage());
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Helpers

    /**
     * Extract temperatures from an input-register array starting at register 8
     * (i.e. the array index of register N is N - 8).
     */
    private ThermiaTemperaturesResponse extractTemperatures(int[] raw, int baseOffset) {
        return new ThermiaTemperaturesResponse(
                toSigned(raw[baseOffset])     / 100.0,   // reg 8: discharge
                toSigned(raw[baseOffset + 1]) / 100.0,   // reg 9: condenser inlet
                toSigned(raw[baseOffset + 2]) / 100.0,   // reg 10: condenser outlet
                toSigned(raw[baseOffset + 3]) / 100.0,   // reg 11: brine inlet
                toSigned(raw[baseOffset + 4]) / 100.0,   // reg 12: brine outlet
                toSigned(raw[baseOffset + 5]) / 100.0,   // reg 13: supply line
                toSigned(raw[baseOffset + 6]) / 100.0,   // reg 14: outdoor
                toSigned(raw[baseOffset + 8]) / 100.0,   // reg 16: DHW top (skip index 7 = reg 15)
                toSigned(raw[baseOffset + 9]) / 100.0,   // reg 17: DHW bottom
                toSigned(raw[baseOffset + 11]) / 100.0   // reg 19: supply setpoint (skip index 10 = reg 18)
        );
    }

    /**
     * Extract temperatures from a getStatus input batch (registers 1–27, index = register - 1).
     * Register 8 is at index 7 in this array.
     */
    private ThermiaTemperaturesResponse extractTemperatures(int[] inputLow) {
        return extractTemperatures(inputLow, REG_IN_DISCHARGE_TEMP - 1);  // base offset = 7
    }

    /** Interpret a raw unsigned 16-bit value as a signed 16-bit integer. */
    private static int toSigned(int raw) {
        return (short) raw;
    }

    /** Combine two consecutive 16-bit registers into a 32-bit value (MSB first). */
    private static long to32bit(int msb, int lsb) {
        return ((long) msb << 16) | (lsb & 0xFFFFL);
    }

    private static void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
