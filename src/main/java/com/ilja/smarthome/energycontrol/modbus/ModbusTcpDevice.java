package com.ilja.smarthome.energycontrol.modbus;

import com.ghgande.j2mod.modbus.io.ModbusTCPTransaction;
import com.ghgande.j2mod.modbus.msg.ModbusRequest;
import com.ghgande.j2mod.modbus.msg.ModbusResponse;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * One Modbus TCP device (heat pump, inverter) shared by every caller in the application.
 *
 * <ul>
 *   <li><b>One transaction at a time.</b> The UI, the data collection and the control loops all talk to the
 *       same device; small Modbus gateways accept only one or two clients and drop or refuse the rest.
 *       A fair lock queues callers instead.</li>
 *   <li><b>Waiting is bounded.</b> If the device stays busy for {@code lockTimeoutMs}, the caller gets the
 *       device's communication exception instead of piling up.</li>
 *   <li><b>A minimum pause between transactions</b> ({@code minGapMs}), whoever sends them.</li>
 *   <li><b>{@link #exclusive}</b> keeps the device for several transactions, e.g. a multi-batch status read,
 *       so nobody else's request lands in between. The lock is reentrant.</li>
 * </ul>
 * A new TCP connection is opened for every transaction, as before. Addresses are raw 0-based PDU
 * addresses; mapping from documented register numbers is up to the caller.
 */
public class ModbusTcpDevice {

    /** Creates the device-specific exception (e.g. ThermiaCommException) for a failed transaction. */
    @FunctionalInterface
    public interface ErrorFactory {
        RuntimeException create(String message, Throwable cause);
    }

    /**
     * Cause attached to the device exception when the caller gave up waiting for its turn: the device is
     * busy with someone else's transaction, which says nothing about whether it is reachable.
     */
    public static final class DeviceBusyException extends RuntimeException {
        DeviceBusyException(String message) {
            super(message);
        }
    }

    /** True if {@code e} (or a cause) means "gave up waiting for the device", not "device failed". */
    public static boolean isBusy(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof DeviceBusyException) {
                return true;
            }
        }
        return false;
    }

    private static final Logger log = LoggerFactory.getLogger(ModbusTcpDevice.class);

    private final String name;
    private final String host;
    private final int port;
    private final int unitId;
    private final int timeoutMs;
    private final long minGapNanos;
    private final long lockTimeoutMs;
    private final ErrorFactory errors;

    private final ReentrantLock lock = new ReentrantLock(true);
    /** End of the previous transaction (System.nanoTime). Guarded by {@link #lock}. */
    private long lastTransactionEnd;
    private boolean anyTransactionYet;

    public ModbusTcpDevice(String name, String host, int port, int unitId, int timeoutMs,
                           long minGapMs, long lockTimeoutMs, ErrorFactory errors) {
        this.name = name;
        this.host = host;
        this.port = port;
        this.unitId = unitId;
        this.timeoutMs = timeoutMs;
        this.minGapNanos = TimeUnit.MILLISECONDS.toNanos(minGapMs);
        this.lockTimeoutMs = lockTimeoutMs;
        this.errors = errors;
    }

    /**
     * Runs {@code work} with exclusive access to the device: transactions it makes are not interleaved with
     * anyone else's. Reentrant, so {@code work} may call the read/write methods of this device.
     */
    public <T> T exclusive(Supplier<T> work) {
        acquire();
        try {
            return work.get();
        } finally {
            lock.unlock();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Transactions (0-based addresses). {@code what} names the registers in the device's own numbering
    // for error messages, e.g. "input registers 8–19".

    public int[] readInputRegisters(int address, int count, String what) {
        ReadInputRegistersResponse response = transaction("read " + what,
                new ReadInputRegistersRequest(address, count), ReadInputRegistersResponse.class);
        int[] values = new int[count];
        for (int i = 0; i < count; i++) {
            values[i] = response.getRegisterValue(i);
        }
        return values;
    }

    public int[] readHoldingRegisters(int address, int count, String what) {
        ReadMultipleRegistersResponse response = transaction("read " + what,
                new ReadMultipleRegistersRequest(address, count), ReadMultipleRegistersResponse.class);
        int[] values = new int[count];
        for (int i = 0; i < count; i++) {
            values[i] = response.getRegisterValue(i);
        }
        return values;
    }

    public void writeHoldingRegister(int address, int value, String what) {
        transaction("write " + what + " = " + value,
                new WriteSingleRegisterRequest(address, new SimpleRegister(value)), ModbusResponse.class);
    }

    public boolean[] readDiscreteInputs(int address, int count, String what) {
        ReadInputDiscretesResponse response = transaction("read " + what,
                new ReadInputDiscretesRequest(address, count), ReadInputDiscretesResponse.class);
        boolean[] values = new boolean[count];
        for (int i = 0; i < count; i++) {
            values[i] = response.getDiscretes().getBit(i);
        }
        return values;
    }

    public boolean[] readCoils(int address, int count, String what) {
        ReadCoilsResponse response = transaction("read " + what,
                new ReadCoilsRequest(address, count), ReadCoilsResponse.class);
        boolean[] values = new boolean[count];
        for (int i = 0; i < count; i++) {
            values[i] = response.getCoils().getBit(i);
        }
        return values;
    }

    public void writeCoil(int address, boolean value, String what) {
        transaction("write " + what + " = " + value, new WriteCoilRequest(address, value), ModbusResponse.class);
    }

    // ---------------------------------------------------------------------------------------------

    private <R extends ModbusResponse> R transaction(String description, ModbusRequest request, Class<R> responseType) {
        return exclusive(() -> {
            pace();
            log.debug("{}: {} ({}:{})", name, description, host, port);
            request.setUnitID(unitId);
            ModbusResponse response;
            TCPMasterConnection connection = connect();
            try {
                ModbusTCPTransaction transaction = new ModbusTCPTransaction(connection);
                // One try. j2mod's default is 5 tries with back-off, which would keep the device (and
                // everyone queued behind it) blocked for ~5 x timeout when the device accepts but never
                // answers. Callers retry on their next run instead.
                transaction.setRetries(1);
                transaction.setRequest(request);
                transaction.execute();
                response = transaction.getResponse();
            } catch (Exception e) {
                throw errors.create("Failed to " + description + " on " + name + ": " + e.getMessage(), e);
            } finally {
                connection.close();
                markTransactionEnd();
            }
            if (!responseType.isInstance(response)) {
                throw errors.create("Failed to " + description + " on " + name + ": unexpected response "
                        + (response == null ? "null" : response.getClass().getSimpleName()), null);
            }
            return responseType.cast(response);
        });
    }

    private TCPMasterConnection connect() {
        try {
            TCPMasterConnection connection = new TCPMasterConnection(InetAddress.getByName(host));
            connection.setPort(port);
            connection.setTimeout(timeoutMs);
            connection.connect();
            return connection;
        } catch (Exception e) {
            markTransactionEnd();
            throw errors.create("Cannot connect to " + name + " at " + host + ":" + port + " — " + e.getMessage(), e);
        }
    }

    private void acquire() {
        try {
            if (!lock.tryLock(lockTimeoutMs, TimeUnit.MILLISECONDS)) {
                String message = name + " busy: waited " + lockTimeoutMs + " ms for another Modbus transaction";
                throw errors.create(message, new DeviceBusyException(message));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw errors.create("Interrupted while waiting for " + name, e);
        }
    }

    private void markTransactionEnd() {
        lastTransactionEnd = System.nanoTime();
        anyTransactionYet = true;
    }

    /** Keeps at least {@code minGapMs} between the end of one transaction and the start of the next. */
    private void pace() {
        if (!anyTransactionYet) {
            return;
        }
        long waitNanos = minGapNanos - (System.nanoTime() - lastTransactionEnd);
        if (waitNanos > 0) {
            try {
                TimeUnit.NANOSECONDS.sleep(waitNanos);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw errors.create("Interrupted while waiting for " + name, e);
            }
        }
    }
}
