package com.ilja.smarthome.energycontrol.thermia.client;

import com.ghgande.j2mod.modbus.io.ModbusTCPTransaction;
import com.ghgande.j2mod.modbus.msg.ReadCoilsRequest;
import com.ghgande.j2mod.modbus.msg.ReadCoilsResponse;
import com.ghgande.j2mod.modbus.msg.ReadInputDiscretesRequest;
import com.ghgande.j2mod.modbus.msg.ReadInputDiscretesResponse;
import com.ghgande.j2mod.modbus.msg.ReadInputRegistersRequest;
import com.ghgande.j2mod.modbus.msg.ReadInputRegistersResponse;
import com.ghgande.j2mod.modbus.msg.ReadMultipleRegistersRequest;
import com.ghgande.j2mod.modbus.msg.ReadMultipleRegistersResponse;
import com.ghgande.j2mod.modbus.msg.WriteCoilRequest;
import com.ghgande.j2mod.modbus.msg.WriteSingleRegisterRequest;
import com.ghgande.j2mod.modbus.net.TCPMasterConnection;
import com.ghgande.j2mod.modbus.procimg.SimpleRegister;
import com.ilja.smarthome.energycontrol.thermia.config.ThermiaProperties;
import com.ilja.smarthome.energycontrol.thermia.exception.ThermiaCommException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetAddress;

/**
 * Low-level Modbus TCP client for the Thermia heat pump.
 *
 * Register addressing:
 *   - Input/holding register methods use 1-based numbers (as in Thermia documentation).
 *     The PDU requires 0-based addressing, so every request subtracts 1 internally.
 *   - Coil and discrete input methods use 0-based addresses (as in Thermia documentation
 *     for coils/discretes, which are not offset the same way).
 *
 * A new TCP connection is opened for every operation — thread-safe without pooling.
 */
@Slf4j
@Component
public class ThermiaModbusClient {

    private final ThermiaProperties props;

    public ThermiaModbusClient(ThermiaProperties props) {
        this.props = props;
    }

    /**
     * Read multiple consecutive input registers (FC04).
     *
     * @param startRegister 1-based start register number
     * @param count         number of registers to read
     * @return array of unsigned 16-bit values
     */
    public int[] readInputRegisters(int startRegister, int count) {
        log.debug("FC04 read {} input registers from reg {} ({}:{})",
                count, startRegister, props.getHost(), props.getPort());
        TCPMasterConnection conn = openConnection();
        try {
            ReadInputRegistersRequest request =
                    new ReadInputRegistersRequest(startRegister - 1, count);
            request.setUnitID(props.getSlaveId());

            ModbusTCPTransaction tx = new ModbusTCPTransaction(conn);
            tx.setRequest(request);
            tx.execute();

            ReadInputRegistersResponse response = (ReadInputRegistersResponse) tx.getResponse();
            int[] values = new int[count];
            for (int i = 0; i < count; i++) {
                values[i] = response.getRegisterValue(i);
            }
            return values;
        } catch (ThermiaCommException e) {
            throw e;
        } catch (Exception e) {
            throw new ThermiaCommException(
                    "Failed to read input registers " + startRegister + "–" + (startRegister + count - 1)
                            + ": " + e.getMessage(), e);
        } finally {
            conn.close();
        }
    }

    /**
     * Read multiple consecutive holding registers (FC03).
     *
     * @param startRegister 1-based start register number
     * @param count         number of registers to read
     * @return array of unsigned 16-bit values
     */
    public int[] readHoldingRegisters(int startRegister, int count) {
        log.debug("FC03 read {} holding registers from reg {} ({}:{})",
                count, startRegister, props.getHost(), props.getPort());
        TCPMasterConnection conn = openConnection();
        try {
            ReadMultipleRegistersRequest request =
                    new ReadMultipleRegistersRequest(startRegister - 1, count);
            request.setUnitID(props.getSlaveId());

            ModbusTCPTransaction tx = new ModbusTCPTransaction(conn);
            tx.setRequest(request);
            tx.execute();

            ReadMultipleRegistersResponse response = (ReadMultipleRegistersResponse) tx.getResponse();
            int[] values = new int[count];
            for (int i = 0; i < count; i++) {
                values[i] = response.getRegisterValue(i);
            }
            return values;
        } catch (ThermiaCommException e) {
            throw e;
        } catch (Exception e) {
            throw new ThermiaCommException(
                    "Failed to read holding registers " + startRegister + "–" + (startRegister + count - 1)
                            + ": " + e.getMessage(), e);
        } finally {
            conn.close();
        }
    }

    /**
     * Write a single holding register (FC06).
     *
     * @param register 1-based register number
     * @param value    unsigned 16-bit value to write
     */
    public void writeHoldingRegister(int register, int value) {
        log.debug("FC06 write reg {} = {} to {}:{}", register, value, props.getHost(), props.getPort());
        TCPMasterConnection conn = openConnection();
        try {
            WriteSingleRegisterRequest request =
                    new WriteSingleRegisterRequest(register - 1, new SimpleRegister(value));
            request.setUnitID(props.getSlaveId());

            ModbusTCPTransaction tx = new ModbusTCPTransaction(conn);
            tx.setRequest(request);
            tx.execute();
        } catch (ThermiaCommException e) {
            throw e;
        } catch (Exception e) {
            throw new ThermiaCommException(
                    "Failed to write register " + register + ": " + e.getMessage(), e);
        } finally {
            conn.close();
        }
    }

    /**
     * Read discrete inputs (FC02) — alarm flags.
     *
     * @param startAddress 0-based address (as in Thermia discrete input documentation)
     * @param count        number of bits to read
     * @return boolean array, index 0 corresponds to startAddress
     */
    public boolean[] readDiscreteInputs(int startAddress, int count) {
        log.debug("FC02 read {} discrete inputs from addr {} ({}:{})",
                count, startAddress, props.getHost(), props.getPort());
        TCPMasterConnection conn = openConnection();
        try {
            ReadInputDiscretesRequest request =
                    new ReadInputDiscretesRequest(startAddress, count);
            request.setUnitID(props.getSlaveId());

            ModbusTCPTransaction tx = new ModbusTCPTransaction(conn);
            tx.setRequest(request);
            tx.execute();

            ReadInputDiscretesResponse response = (ReadInputDiscretesResponse) tx.getResponse();
            boolean[] values = new boolean[count];
            for (int i = 0; i < count; i++) {
                values[i] = response.getDiscretes().getBit(i);
            }
            return values;
        } catch (ThermiaCommException e) {
            throw e;
        } catch (Exception e) {
            throw new ThermiaCommException(
                    "Failed to read discrete inputs at addr " + startAddress + ": " + e.getMessage(), e);
        } finally {
            conn.close();
        }
    }

    /**
     * Read coils (FC01) — function enable states.
     *
     * @param startAddress 0-based address (as in Thermia coil documentation)
     * @param count        number of coils to read
     * @return boolean array, index 0 corresponds to startAddress
     */
    public boolean[] readCoils(int startAddress, int count) {
        log.debug("FC01 read {} coils from addr {} ({}:{})",
                count, startAddress, props.getHost(), props.getPort());
        TCPMasterConnection conn = openConnection();
        try {
            ReadCoilsRequest request = new ReadCoilsRequest(startAddress, count);
            request.setUnitID(props.getSlaveId());

            ModbusTCPTransaction tx = new ModbusTCPTransaction(conn);
            tx.setRequest(request);
            tx.execute();

            ReadCoilsResponse response = (ReadCoilsResponse) tx.getResponse();
            boolean[] values = new boolean[count];
            for (int i = 0; i < count; i++) {
                values[i] = response.getCoils().getBit(i);
            }
            return values;
        } catch (ThermiaCommException e) {
            throw e;
        } catch (Exception e) {
            throw new ThermiaCommException(
                    "Failed to read coils at addr " + startAddress + ": " + e.getMessage(), e);
        } finally {
            conn.close();
        }
    }

    /**
     * Write a single coil (FC05) — enable or disable a function.
     *
     * @param address 0-based coil address
     * @param value   true = enabled, false = disabled
     */
    public void writeCoil(int address, boolean value) {
        log.debug("FC05 write coil {} = {} to {}:{}", address, value, props.getHost(), props.getPort());
        TCPMasterConnection conn = openConnection();
        try {
            WriteCoilRequest request = new WriteCoilRequest(address, value);
            request.setUnitID(props.getSlaveId());

            ModbusTCPTransaction tx = new ModbusTCPTransaction(conn);
            tx.setRequest(request);
            tx.execute();
        } catch (ThermiaCommException e) {
            throw e;
        } catch (Exception e) {
            throw new ThermiaCommException(
                    "Failed to write coil " + address + ": " + e.getMessage(), e);
        } finally {
            conn.close();
        }
    }

    // -------------------------------------------------------------------------

    private TCPMasterConnection openConnection() {
        try {
            InetAddress addr = InetAddress.getByName(props.getHost());
            TCPMasterConnection conn = new TCPMasterConnection(addr);
            conn.setPort(props.getPort());
            conn.setTimeout(props.getTimeoutMs());
            conn.connect();
            return conn;
        } catch (Exception e) {
            throw new ThermiaCommException(
                    "Cannot connect to Thermia heat pump at " + props.getHost() + ":" + props.getPort()
                            + " — " + e.getMessage(), e);
        }
    }
}
