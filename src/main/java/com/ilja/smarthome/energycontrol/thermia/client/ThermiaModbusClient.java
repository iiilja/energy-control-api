package com.ilja.smarthome.energycontrol.thermia.client;

import com.ilja.smarthome.energycontrol.modbus.ModbusTcpDevice;
import com.ilja.smarthome.energycontrol.thermia.config.ThermiaProperties;
import com.ilja.smarthome.energycontrol.thermia.exception.ThermiaCommException;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Modbus TCP client for the Thermia heat pump.
 *
 * <p>Register addressing:
 * <ul>
 *   <li>Input/holding register methods use 1-based numbers (as in the Thermia documentation);
 *       the PDU is 0-based, so 1 is subtracted here.</li>
 *   <li>Coil and discrete input methods use 0-based addresses, as in the Thermia documentation for those.</li>
 * </ul>
 * All callers share one {@link ModbusTcpDevice}: one transaction at a time, at least
 * {@value #MIN_GAP_MS} ms apart (the pause the original ESP32 firmware used between batches).
 */
@Component
public class ThermiaModbusClient {

    static final long MIN_GAP_MS = 50;

    private final ModbusTcpDevice device;

    public ThermiaModbusClient(ThermiaProperties props) {
        this.device = new ModbusTcpDevice("Thermia heat pump", props.getHost(), props.getPort(), props.getSlaveId(),
                props.getTimeoutMs(), MIN_GAP_MS, props.getLockTimeoutMs(), ThermiaCommException::new);
    }

    /** Runs several transactions without anyone else's in between (e.g. a multi-batch status read). */
    public <T> T exclusive(Supplier<T> work) {
        return device.exclusive(work);
    }

    /** Read consecutive input registers (FC04), 1-based start register. Values are unsigned 16-bit. */
    public int[] readInputRegisters(int startRegister, int count) {
        return device.readInputRegisters(startRegister - 1, count, "input registers " + range(startRegister, count));
    }

    /** Read consecutive holding registers (FC03), 1-based start register. Values are unsigned 16-bit. */
    public int[] readHoldingRegisters(int startRegister, int count) {
        return device.readHoldingRegisters(startRegister - 1, count, "holding registers " + range(startRegister, count));
    }

    /** Write a single holding register (FC06), 1-based register number. */
    public void writeHoldingRegister(int register, int value) {
        device.writeHoldingRegister(register - 1, value, "holding register " + register);
    }

    /** Read discrete inputs (FC02) — alarm flags; 0-based address. */
    public boolean[] readDiscreteInputs(int startAddress, int count) {
        return device.readDiscreteInputs(startAddress, count, "discrete inputs at " + startAddress);
    }

    /** Read coils (FC01) — function enable states; 0-based address. */
    public boolean[] readCoils(int startAddress, int count) {
        return device.readCoils(startAddress, count, "coils at " + startAddress);
    }

    /** Write a single coil (FC05); 0-based address. */
    public void writeCoil(int address, boolean value) {
        device.writeCoil(address, value, "coil " + address);
    }

    private static String range(int start, int count) {
        return count == 1 ? String.valueOf(start) : start + "–" + (start + count - 1);
    }
}
