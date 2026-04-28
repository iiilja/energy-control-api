package com.ilja.smarthome.energycontrol.sungrow.client;

import com.ghgande.j2mod.modbus.io.ModbusTCPTransaction;
import com.ghgande.j2mod.modbus.msg.ReadInputRegistersRequest;
import com.ghgande.j2mod.modbus.msg.ReadInputRegistersResponse;
import com.ghgande.j2mod.modbus.msg.ReadMultipleRegistersRequest;
import com.ghgande.j2mod.modbus.msg.ReadMultipleRegistersResponse;
import com.ghgande.j2mod.modbus.msg.WriteSingleRegisterRequest;
import com.ghgande.j2mod.modbus.net.TCPMasterConnection;
import com.ghgande.j2mod.modbus.procimg.SimpleRegister;
import com.ilja.smarthome.energycontrol.sungrow.config.SungrowProperties;
import com.ilja.smarthome.energycontrol.sungrow.exception.SungrowCommunicationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetAddress;

/**
 * Low-level Modbus TCP client for the Sungrow SG15RT inverter.
 *
 * Register addressing note: the Sungrow documentation uses 1-based register numbers
 * (e.g. register 5007). The Modbus PDU uses 0-based addressing, so every request
 * subtracts 1 from the documented number.
 *
 * A new TCP connection is opened for every operation. This keeps the code simple and
 * thread-safe without a pool; the overhead is negligible at home-automation polling rates.
 */
@Slf4j
@Component
public class SungrowModbusClient {

    private final SungrowProperties props;

    public SungrowModbusClient(SungrowProperties props) {
        this.props = props;
    }

    /**
     * Read a single holding register (FC03).
     *
     * @param register 1-based register number as in Sungrow documentation
     * @return unsigned 16-bit register value (0–65535)
     */
    public int readHoldingRegister(int register) {
        log.debug("Reading holding register {} from {}:{}", register, props.getHost(), props.getPort());

        TCPMasterConnection conn = openConnection();
        try {
            ReadMultipleRegistersRequest request =
                    new ReadMultipleRegistersRequest(register - 1, 1);
            request.setUnitID(props.getSlaveId());

            ModbusTCPTransaction tx = new ModbusTCPTransaction(conn);
            tx.setRequest(request);
            tx.execute();

            ReadMultipleRegistersResponse response = (ReadMultipleRegistersResponse) tx.getResponse();
            int value = response.getRegisterValue(0);
            log.debug("Register {} = {}", register, value);
            return value;

        } catch (SungrowCommunicationException e) {
            throw e;
        } catch (Exception e) {
            throw new SungrowCommunicationException(
                    "Failed to read register " + register + ": " + e.getMessage(), e);
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
        log.debug("Reading {} holding registers from {} ({}:{})", count, startRegister,
                props.getHost(), props.getPort());

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

        } catch (SungrowCommunicationException e) {
            throw e;
        } catch (Exception e) {
            throw new SungrowCommunicationException(
                    "Failed to read registers " + startRegister + "–" + (startRegister + count - 1)
                            + ": " + e.getMessage(), e);
        } finally {
            conn.close();
        }
    }

    /**
     * Write a single holding register (FC06).
     *
     * @param register 1-based register number as in Sungrow documentation
     * @param value    unsigned 16-bit value to write
     */
    public void writeHoldingRegister(int register, int value) {
        log.debug("Writing register {} = {} to {}:{}", register, value, props.getHost(), props.getPort());

        TCPMasterConnection conn = openConnection();
        try {
            WriteSingleRegisterRequest request =
                    new WriteSingleRegisterRequest(register - 1, new SimpleRegister(value));
            request.setUnitID(props.getSlaveId());

            ModbusTCPTransaction tx = new ModbusTCPTransaction(conn);
            tx.setRequest(request);
            tx.execute();

            log.debug("Successfully wrote register {} = {}", register, value);

        } catch (SungrowCommunicationException e) {
            throw e;
        } catch (Exception e) {
            throw new SungrowCommunicationException(
                    "Failed to write register " + register + ": " + e.getMessage(), e);
        } finally {
            conn.close();
        }
    }

    /**
     * Read a single input register (FC04).
     *
     * @param register 1-based register number as in Sungrow documentation
     * @return unsigned 16-bit register value (0–65535)
     */
    public int readInputRegister(int register) {
        log.debug("Reading input register {} from {}:{}", register, props.getHost(), props.getPort());

        TCPMasterConnection conn = openConnection();
        try {
            ReadInputRegistersRequest request =
                    new ReadInputRegistersRequest(register - 1, 1);
            request.setUnitID(props.getSlaveId());

            ModbusTCPTransaction tx = new ModbusTCPTransaction(conn);
            tx.setRequest(request);
            tx.execute();

            ReadInputRegistersResponse response = (ReadInputRegistersResponse) tx.getResponse();
            int value = response.getRegisterValue(0);
            log.debug("Input register {} = {}", register, value);
            return value;

        } catch (SungrowCommunicationException e) {
            throw e;
        } catch (Exception e) {
            throw new SungrowCommunicationException(
                    "Failed to read input register " + register + ": " + e.getMessage(), e);
        } finally {
            conn.close();
        }
    }

    /**
     * Read multiple consecutive input registers (FC04).
     *
     * @param startRegister 1-based start register number
     * @param count         number of registers to read
     * @return array of unsigned 16-bit values
     */
    public int[] readInputRegisters(int startRegister, int count) {
        log.debug("Reading {} input registers from {} ({}:{})", count, startRegister,
                props.getHost(), props.getPort());

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

        } catch (SungrowCommunicationException e) {
            throw e;
        } catch (Exception e) {
            throw new SungrowCommunicationException(
                    "Failed to read input registers " + startRegister + "–" + (startRegister + count - 1)
                            + ": " + e.getMessage(), e);
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
            throw new SungrowCommunicationException(
                    "Cannot connect to Sungrow inverter at " + props.getHost() + ":" + props.getPort()
                            + " — " + e.getMessage(), e);
        }
    }
}
