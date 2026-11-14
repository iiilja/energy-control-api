package com.ilja.smarthome.energycontrol.scheduler;

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
 * A weekly template change re-plans at once, so planned slots never drive an outdated template.
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

    @Scheduled(cron = "${heating.price_optimization.cron:0 5 * * * *}", zone = "Europe/Tallinn")
    public void plan() {
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
