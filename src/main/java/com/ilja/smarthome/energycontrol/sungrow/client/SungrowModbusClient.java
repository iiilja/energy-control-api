package com.ilja.smarthome.energycontrol.sungrow.client;

import com.ilja.smarthome.energycontrol.modbus.ModbusTcpDevice;
import com.ilja.smarthome.energycontrol.sungrow.config.SungrowProperties;
import com.ilja.smarthome.energycontrol.sungrow.exception.SungrowCommunicationException;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Modbus TCP client for the Sungrow SG15RT inverter.
 *
 * <p>Register numbers are 1-based as in the Sungrow documentation (e.g. 5007); the PDU is 0-based,
 * so 1 is subtracted here. All callers share one {@link ModbusTcpDevice}: one transaction at a time,
 * at least {@value #MIN_GAP_MS} ms apart (the WiNet dongle handles parallel clients poorly).
 */
@Component
public class SungrowModbusClient {

    static final long MIN_GAP_MS = 50;

    private final ModbusTcpDevice device;

    public SungrowModbusClient(SungrowProperties props) {
        this.device = new ModbusTcpDevice("Sungrow inverter", props.getHost(), props.getPort(), props.getSlaveId(),
                props.getTimeoutMs(), MIN_GAP_MS, props.getLockTimeoutMs(), SungrowCommunicationException::new);
    }

    /** Runs several transactions without anyone else's in between (e.g. a full status read). */
    public <T> T exclusive(Supplier<T> work) {
        return device.exclusive(work);
    }

    /** Read a single holding register (FC03), 1-based. Unsigned 16-bit. */
    public int readHoldingRegister(int register) {
        return readHoldingRegisters(register, 1)[0];
    }

    /** Read consecutive holding registers (FC03), 1-based start register. */
    public int[] readHoldingRegisters(int startRegister, int count) {
        return device.readHoldingRegisters(startRegister - 1, count, "holding registers " + range(startRegister, count));
    }

    /** Write a single holding register (FC06), 1-based. */
    public void writeHoldingRegister(int register, int value) {
        device.writeHoldingRegister(register - 1, value, "holding register " + register);
    }

    /** Read a single input register (FC04), 1-based. Unsigned 16-bit. */
    public int readInputRegister(int register) {
        return readInputRegisters(register, 1)[0];
    }

    /** Read consecutive input registers (FC04), 1-based start register. */
    public int[] readInputRegisters(int startRegister, int count) {
        return device.readInputRegisters(startRegister - 1, count, "input registers " + range(startRegister, count));
    }

    private static String range(int start, int count) {
        return count == 1 ? String.valueOf(start) : start + "–" + (start + count - 1);
    }
}
