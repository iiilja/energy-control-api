package com.ilja.smarthome.energycontrol.scheduler;

import com.ilja.smarthome.energycontrol.service.ConfigurationChangedEvent;
import com.ilja.smarthome.energycontrol.service.PriceOptimizationService;
import com.ilja.smarthome.energycontrol.service.WeeklyScheduleChangedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Re-plans today and tomorrow every hour (5 minutes past, between the control loop's runs).
 * Tomorrow's prices arrive in the afternoon, so its plan appears with the first run after that.
 * Runs also when the optimisation is off, so switching it off removes its planned slots.
 * A weekly template change or a settings change through the API re-plans at once.
 */
@Component
@Slf4j
public class PriceOptimizationScheduler {

    private static final ZoneId EUROPE_TALLINN = ZoneId.of("Europe/Tallinn");

    private final PriceOptimizationService optimizationService;
    private final Clock clock;

    public PriceOptimizationScheduler(PriceOptimizationService optimizationService, Clock clock) {
        this.optimizationService = optimizationService;
        this.clock = clock;
    }

    /** After the template change has committed (optimize runs in its own transaction). */
    @TransactionalEventListener
    public void onWeeklyScheduleChanged(WeeklyScheduleChangedEvent event) {
        log.info("Weekly template changed, re-planning price optimisation");
        plan();
    }

    /** A price optimisation setting (or the default setpoint, the template's fallback) changed through the API. */
    @TransactionalEventListener
    public void onConfigurationChanged(ConfigurationChangedEvent event) {
        if (event.key().startsWith("heating.price_optimization.") || event.key().equals("heating.setpoint.default")) {
            log.info("{} changed, re-planning price optimisation", event.key());
            plan();
        }
    }

    @Scheduled(cron = "${heating.price_optimization.cron:0 5 * * * *}", zone = "Europe/Tallinn")
    // synchronized: the hourly run, a template change and a settings change must not re-plan at the
    // same time (each would replace the future slots from its own snapshot)
    public synchronized void plan() {
        LocalDate today = LocalDate.now(clock.withZone(EUROPE_TALLINN));
        for (LocalDate day : new LocalDate[]{today, today.plusDays(1)}) {
            try {
                optimizationService.optimize(day);
            } catch (Exception e) {
                log.error("Price optimisation for {} failed", day, e);
            }
        }
    }
}
