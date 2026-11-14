package com.ilja.smarthome.energycontrol.dto.heating;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * A day's prices as the price optimisation sees them, with the heating setpoints, for the UI chart.
 *
 * @param enabled             whether the optimisation is switched on (kinds and optimized setpoints are
 *                            shown either way, so the settings can be tuned before switching it on)
 * @param settingsValid       false if a setting is invalid: the optimisation is then off and the
 *                            defaults are shown
 * @param complete            whether every 15-minute price of the day is known; only then are peaks
 *                            computed (median, threshold and kinds are empty/NORMAL otherwise)
 * @param median              the day's median price, EUR/MWh
 * @param threshold           median + peakThreshold: slots at or above it are peaks
 */
public record PricePlanResponse(
        LocalDate date,
        boolean enabled,
        boolean settingsValid,
        boolean complete,
        BigDecimal median,
        BigDecimal threshold,
        BigDecimal peakThreshold,
        BigDecimal lower,
        BigDecimal raise,
        int compensationMinutes,
        List<Slot> slots
) {
    /**
     * @param timestamp          slot start, ISO offset date-time in Tallinn time
     * @param kind               NORMAL, PEAK or COMPENSATION
     * @param templateSetpoint   the weekly template's setpoint
     * @param optimizedSetpoint  template shifted for the slot's kind (what the optimisation aims for)
     * @param scheduledSetpoint  what the control loop will use (the stored slot override, otherwise the
     *                           template); an active manual override still wins over it
     * @param source             where scheduledSetpoint comes from: TEMPLATE, PRICE or MANUAL
     */
    public record Slot(
            String timestamp,
            BigDecimal price,
            String kind,
            BigDecimal templateSetpoint,
            BigDecimal optimizedSetpoint,
            BigDecimal scheduledSetpoint,
            String source
    ) {
    }
}
