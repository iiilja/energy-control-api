package com.ilja.smarthome.energycontrol.thermia.dto;

/**
 * Complete status snapshot of the Thermia heat pump.
 *
 * <p>operationMode: 1=OFF, 2=Standby, 3=ON/Auto (FC03 reg 1)</p>
 * <p>currentDemand: 1=Manual, 2=Defrost, 3=DHW, 4=Heating, 5=Cooling,
 *    6=Pool, 7=Anti-legionella, 98=Standby, 99=No demand, 100=OFF (FC04 reg 2)</p>
 * <p>hotGasPumpEnabled: FC01/FC05 coil 14 (de-facto 15)</p>
 * <p>antiLegionellaEnabled: FC01/FC05 coil 24 (de-facto 25)</p>
 * <p>internalHeaterEnabled: FC01/FC05 coil 4 (de-facto 5)</p>
 * <p>internalHeaterStep: FC04 reg 68 (de-facto 30068), 0=off, 1-3=active step</p>
 */
public record ThermiaStatusResponse(
        ThermiaTemperaturesResponse temperatures,
        int operationMode,
        int currentDemand,
        int compressorRpm,
        double compressorSpeedPercent,
        long compressorHours,
        long heatingHours,
        long externalHeaterHours,
        boolean alarmClassA,
        boolean alarmClassB,
        boolean alarmClassC,
        double heatingSetpoint,
        double[] heatCurveX,
        double[] heatCurveY,
        boolean tapWaterEnabled,
        boolean heatingEnabled,
        boolean coolingEnabled,
        boolean hotGasPumpEnabled,
        boolean antiLegionellaEnabled,
        boolean internalHeaterEnabled,
        int internalHeaterStep
) {}
