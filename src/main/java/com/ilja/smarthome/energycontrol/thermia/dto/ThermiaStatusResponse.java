package com.ilja.smarthome.energycontrol.thermia.dto;

/**
 * Complete status snapshot of the Thermia heat pump.
 *
 * <p>operationMode: 1=OFF, 2=Standby, 3=ON/Auto (FC03 reg 1)</p>
 * <p>currentDemand: 1=Manual, 2=Defrost, 3=DHW, 4=Heating, 5=Cooling,
 *    6=Pool, 7=Anti-legionella, 98=Standby, 99=No demand, 100=OFF (FC04 reg 2)</p>
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
        boolean dhwEnabled,
        boolean heatingEnabled,
        boolean coolingEnabled
) {}
