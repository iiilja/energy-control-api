package com.ilja.smarthome.energycontrol.sungrow.dto;

/**
 * Snapshot of the Sungrow inverter state read from the Modbus registers.
 *
 * @param running            true if inverter is running (register 5006 = 0xCF)
 * @param powerLimitEnabled  true if feed-in limitation is active (register 5007 = 0xAA)
 * @param powerLimitPercent  current limit as a percentage of rated power (register 5008, unit 0.1%)
 * @param powerLimitKw       current limit in kW (register 5039, unit 0.1 kW)
 * @param activePowerW       current AC output power in W (FC04 registers 5031–5032, U32)
 * @param dailyEnergyKwh     today's energy yield in kWh (FC04 register 5003, U16, unit 0.1 kWh)
 * @param totalEnergyKwh     lifetime energy yield in kWh (FC04 registers 5004–5005, U32)
 */
public record SungrowStatusResponse(
        boolean running,
        boolean powerLimitEnabled,
        double powerLimitPercent,
        double powerLimitKw,
        double activePowerW,
        double dailyEnergyKwh,
        double totalEnergyKwh
) {}
