package com.ilja.smarthome.energycontrol.thermia.service;

import com.ilja.smarthome.energycontrol.exception.InvalidValueException;
import com.ilja.smarthome.energycontrol.thermia.client.ThermiaModbusClient;
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

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

    private ThermiaHeatPumpService service;

    @BeforeEach
    void setUp() {
        service = new ThermiaHeatPumpService(modbusClient);
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
}
