package com.ilja.smarthome.energycontrol.modbus;

import com.ghgande.j2mod.modbus.procimg.InputRegister;
import com.ghgande.j2mod.modbus.procimg.SimpleDigitalIn;
import com.ghgande.j2mod.modbus.procimg.SimpleDigitalOut;
import com.ghgande.j2mod.modbus.procimg.SimpleInputRegister;
import com.ghgande.j2mod.modbus.procimg.SimpleProcessImage;
import com.ghgande.j2mod.modbus.procimg.SimpleRegister;
import com.ghgande.j2mod.modbus.slave.ModbusSlave;
import com.ghgande.j2mod.modbus.slave.ModbusSlaveFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ModbusTcpDevice} against a real Modbus TCP slave (j2mod, on localhost): register IO, error
 * mapping, and above all that callers sharing one device never hit the slave at the same time.
 */
class ModbusTcpDeviceTest {

    /** Counts requests the slave is serving at the same moment. */
    private static final class InstrumentedImage extends SimpleProcessImage {
        final AtomicInteger inFlight = new AtomicInteger();
        final AtomicInteger maxInFlight = new AtomicInteger();

        InstrumentedImage() {
            super(1);
        }

        @Override
        public InputRegister[] getInputRegisterRange(int offset, int count) {
            maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
            try {
                Thread.sleep(30); // long enough for overlapping requests to be seen
                return super.getInputRegisterRange(offset, count);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            } finally {
                inFlight.decrementAndGet();
            }
        }
    }

    private InstrumentedImage image;
    private ModbusSlave slave;
    private int port;
    private ExecutorService pool;

    @BeforeEach
    void startSlave() throws Exception {
        port = freePort();
        image = new InstrumentedImage();
        for (int i = 0; i < 30; i++) {
            image.addInputRegister(new SimpleInputRegister(i * 10));
            image.addRegister(new SimpleRegister(i));
            image.addDigitalOut(new SimpleDigitalOut(i % 2 == 0));  // coils
            image.addDigitalIn(new SimpleDigitalIn(i % 3 == 0));   // discrete inputs
        }
        slave = ModbusSlaveFactory.createTCPSlave(port, 8);
        slave.addProcessImage(1, image);
        slave.open();
        pool = Executors.newFixedThreadPool(8);
    }

    @AfterEach
    void stopSlave() {
        pool.shutdownNow();
        ModbusSlaveFactory.close(slave);
    }

    @Test
    void readsAndWritesAllRegisterTypes() {
        ModbusTcpDevice device = device(port, 5000);

        assertThat(device.readInputRegisters(5, 3, "input registers 6–8")).containsExactly(50, 60, 70);

        device.writeHoldingRegister(3, 1234, "holding register 4");
        assertThat(device.readHoldingRegisters(3, 1, "holding register 4")).containsExactly(1234);

        assertThat(device.readCoils(0, 4, "coils")).containsExactly(true, false, true, false);
        device.writeCoil(1, true, "coil 1");
        assertThat(device.readCoils(1, 1, "coil 1")).containsExactly(true);

        assertThat(device.readDiscreteInputs(0, 4, "discrete inputs")).containsExactly(true, false, false, true);
    }

    @Test
    @DisplayName("callers sharing a device never reach the slave at the same time")
    void sharedDeviceSerializesTransactions() throws Exception {
        ModbusTcpDevice device = device(port, 10_000);
        List<Future<int[]>> results = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            results.add(pool.submit(() -> device.readInputRegisters(0, 5, "input registers 1–5")));
        }
        for (Future<int[]> r : results) {
            assertThat(r.get(30, TimeUnit.SECONDS)).containsExactly(0, 10, 20, 30, 40);
        }

        assertThat(image.maxInFlight.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("control: separate devices (no shared lock) do overlap, so the probe above can see it")
    void separateDevicesOverlap() throws Exception {
        List<Future<int[]>> results = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            ModbusTcpDevice own = device(port, 10_000);
            for (int j = 0; j < 3; j++) {
                results.add(pool.submit(() -> own.readInputRegisters(0, 5, "input registers 1–5")));
            }
        }
        for (Future<int[]> r : results) {
            r.get(30, TimeUnit.SECONDS);
        }

        assertThat(image.maxInFlight.get()).isGreaterThan(1);
    }

    @Test
    @DisplayName("keeps the minimum pause between transactions")
    void pacesTransactions() {
        ModbusTcpDevice device = device(port, 5000);
        device.readHoldingRegisters(0, 1, "holding register 1"); // first one is not delayed

        long start = System.nanoTime();
        for (int i = 0; i < 5; i++) {
            device.readHoldingRegisters(0, 1, "holding register 1");
        }
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertThat(elapsedMs).isGreaterThanOrEqualTo(5 * 50);
    }

    @Test
    @DisplayName("exclusive() is reentrant and keeps several transactions together")
    void exclusiveIsReentrant() {
        ModbusTcpDevice device = device(port, 5000);

        int[] both = device.exclusive(() -> new int[]{
                device.readHoldingRegisters(2, 1, "holding register 3")[0],
                device.readInputRegisters(1, 1, "input register 2")[0]});

        assertThat(both).containsExactly(2, 10);
    }

    @Test
    @DisplayName("a caller that cannot get the device within the lock timeout gets the device error")
    void busyDeviceTimesOut() throws Exception {
        ModbusTcpDevice device = device(port, 100);
        CountDownLatch held = new CountDownLatch(1);
        Future<?> holder = pool.submit(() -> device.exclusive(() -> {
            held.countDown();
            sleep(500);
            return null;
        }));
        held.await();

        assertThatThrownBy(() -> device.readInputRegisters(0, 1, "input register 1"))
                .isInstanceOf(DeviceException.class)
                .hasMessageContaining("busy")
                .satisfies(e -> assertThat(ModbusTcpDevice.isBusy(e)).isTrue());

        holder.get();
        assertThat(device.readInputRegisters(2, 1, "input register 3")).containsExactly(20);
    }

    @Test
    @DisplayName("a Modbus exception from the slave becomes the device error, naming the registers")
    void slaveExceptionIsMapped() {
        ModbusTcpDevice device = device(port, 5000);

        assertThatThrownBy(() -> device.readInputRegisters(1000, 1, "input register 1001"))
                .isInstanceOf(DeviceException.class)
                .hasMessageStartingWith("Failed to read input register 1001 on test device");
    }

    @Test
    @DisplayName("an unreachable device gives the device error")
    void connectionRefusedIsMapped() throws Exception {
        ModbusTcpDevice unreachable = device(freePort(), 1000);

        assertThatThrownBy(() -> unreachable.readInputRegisters(0, 1, "input register 1"))
                .isInstanceOf(DeviceException.class)
                .hasMessageStartingWith("Cannot connect to test device");
    }

    @Test
    @DisplayName("a device that accepts but never answers is tried once, not five times (j2mod's default)")
    void silentDeviceIsTriedOnce() throws Exception {
        AtomicInteger connections = new AtomicInteger();
        try (ServerSocket silent = new ServerSocket(0)) {
            pool.submit(() -> {
                List<java.net.Socket> open = new ArrayList<>();
                try {
                    while (true) {
                        open.add(silent.accept()); // accept, never answer
                        connections.incrementAndGet();
                    }
                } catch (IOException closed) {
                    return null;
                }
            });
            ModbusTcpDevice device = new ModbusTcpDevice("silent device", "127.0.0.1", silent.getLocalPort(), 1,
                    1000, 50, 30_000, DeviceException::new);

            long start = System.nanoTime();
            assertThatThrownBy(() -> device.readInputRegisters(0, 1, "input register 1"))
                    .isInstanceOf(DeviceException.class)
                    .satisfies(e -> assertThat(ModbusTcpDevice.isBusy(e)).isFalse());
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

            assertThat(connections.get()).isEqualTo(1);
            assertThat(elapsedMs).isLessThan(3000); // 5 tries with back-off would take ~9 s
        }
    }

    // ------------------------------------------------------------------------

    /** Stands in for ThermiaCommException / SungrowCommunicationException. */
    static final class DeviceException extends RuntimeException {
        DeviceException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static ModbusTcpDevice device(int port, long lockTimeoutMs) {
        return new ModbusTcpDevice("test device", "127.0.0.1", port, 1, 2000, 50, lockTimeoutMs, DeviceException::new);
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
