package com.ilja.smarthome.energycontrol.thermia.dto;

/**
 * Temperature readings from the Thermia heat pump (all in °C).
 *
 * @param dischargeTemp          refrigerant discharge temperature (FC04 reg 8)
 * @param condenserInletTemp     condenser inlet from heat exchanger (FC04 reg 9)
 * @param condenserOutletTemp    condenser outlet / return (FC04 reg 10)
 * @param brineInletTemp         ground loop input temperature (FC04 reg 11)
 * @param brineOutletTemp        ground loop output temperature (FC04 reg 12)
 * @param systemSupplyLineTemp   measured supply line temperature (FC04 reg 13)
 * @param outdoorTemp            outdoor ambient temperature (FC04 reg 14)
 * @param dhwTopTemp             domestic hot water tank top (FC04 reg 16)
 * @param dhwBottomTemp          domestic hot water tank bottom (FC04 reg 17)
 * @param systemSupplySetpoint   calculated supply line target temperature (FC04 reg 19)
 */
public record ThermiaTemperaturesResponse(
        double dischargeTemp,
        double condenserInletTemp,
        double condenserOutletTemp,
        double brineInletTemp,
        double brineOutletTemp,
        double systemSupplyLineTemp,
        double outdoorTemp,
        double dhwTopTemp,
        double dhwBottomTemp,
        double systemSupplySetpoint
) {}
