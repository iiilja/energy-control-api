package com.ilja.smarthome.energycontrol.service;

import com.ilja.smarthome.energycontrol.domain.model.HeatingControlState;
import com.ilja.smarthome.energycontrol.domain.model.HeatingManualOverride;
import com.ilja.smarthome.energycontrol.domain.model.HeatingManualOverride.Source;
import com.ilja.smarthome.energycontrol.exception.InvalidValueException;
import com.ilja.smarthome.energycontrol.repository.HeatingControlStateRepository;
import com.ilja.smarthome.energycontrol.repository.HeatingManualOverrideRepository;
import com.ilja.smarthome.energycontrol.thermia.exception.ThermiaCommException;
import com.ilja.smarthome.energycontrol.thermia.service.ThermiaHeatPumpService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Keeps the heat pump's comfort setpoint where it should be.
 *
 * <p>Each {@link #reconcile()} run (every 15 minutes, and right after an override change via the API):
 * <ol>
 *   <li>reads the setpoint currently on the heat pump;</li>
 *   <li>ends a manual override whose time is up;</li>
 *   <li>if the reading is neither what the heat pump reported after our last write nor a write of ours that
 *       was still unconfirmed, someone changed it by hand: that value becomes a manual override for
 *       {@code heating.setpoint.manual_override.hours};</li>
 *   <li>works out the desired setpoint: active manual override, otherwise the schedule
 *       ({@link HeatingSetpointService#scheduledSetpointAt});</li>
 *   <li>writes it only if it differs from what is on the heat pump.</li>
 * </ol>
 * Nothing is remembered per schedule row: the decision is remade every run, so a missed run or a failed
 * write is corrected on the next one. Any change that did not come from this loop (panel, a reboot that
 * resets the value, a direct write through the Thermia API) is treated as a manual change.
 */
@Slf4j
@Service
public class HeatingControlService {

    static final String OVERRIDE_HOURS_KEY = "heating.setpoint.manual_override.hours";
    static final double DEFAULT_OVERRIDE_HOURS = 4.0;

    private final ThermiaHeatPumpService thermiaService;
    private final HeatingSetpointService heatingSetpointService;
    private final HeatingManualOverrideRepository overrideRepository;
    private final HeatingControlStateRepository stateRepository;
    private final ConfigurationService configService;
    private final Clock clock;

    public HeatingControlService(ThermiaHeatPumpService thermiaService,
                                 HeatingSetpointService heatingSetpointService,
                                 HeatingManualOverrideRepository overrideRepository,
                                 HeatingControlStateRepository stateRepository,
                                 ConfigurationService configService,
                                 Clock clock) {
        this.thermiaService = thermiaService;
        this.heatingSetpointService = heatingSetpointService;
        this.overrideRepository = overrideRepository;
        this.stateRepository = stateRepository;
        this.configService = configService;
        this.clock = clock;
    }

    /**
     * One control step. {@code synchronized}: the scheduler and the override API may call it at the same
     * time, and two concurrent runs would misread each other's write as a manual change.
     *
     * @throws ThermiaCommException if the heat pump cannot be reached (retried next run)
     */
    public synchronized void reconcile() {
        Instant now = Instant.now(clock);
        BigDecimal actual = readPumpSetpoint();
        HeatingControlState state = stateRepository.findById(HeatingControlState.SINGLETON_ID).orElse(null);
        Optional<HeatingManualOverride> override = overrideRepository.findOpen();

        // 1. Override time is up: back to the schedule. The state already holds the override value as our
        //    last read-back, so the value still on the heat pump is not mistaken for a manual change.
        if (override.isPresent() && !override.get().isActiveAt(now)) {
            HeatingManualOverride ended = override.get();
            ended.setEndedAt(now);
            overrideRepository.save(ended);
            log.info("Manual override {} °C ({}) ended, returning to the schedule",
                    ended.getSetpoint(), ended.getSource());
            override = Optional.empty();
        }

        // 2. A write of ours that was not confirmed last time (error after the pump applied it): confirm it.
        if (state != null && state.getPendingSetpoint() != null && sameSetpoint(actual, state.getPendingSetpoint())) {
            state.setLastDesiredSetpoint(state.getPendingSetpoint());
            state.setLastReadBackSetpoint(actual);
            log.info("Earlier write of {} °C confirmed on the heat pump", actual);
        }

        // 3. Changed by hand since our last write?
        if (state != null && !sameSetpoint(actual, state.getLastReadBackSetpoint())) {
            Instant until = overrideEnd(now, overrideDuration());
            if (override.isPresent()) {
                HeatingManualOverride current = override.get();
                log.info("Setpoint changed by hand during an override: {} -> {} °C, kept until {}",
                        current.getSetpoint(), actual, until);
                current.setSetpoint(actual);
                current.setSource(Source.HEAT_PUMP);
                current.setValidUntil(until);
                overrideRepository.save(current);
            } else {
                log.info("Setpoint changed on the heat pump from {} to {} °C, kept until {} before returning to the schedule",
                        state.getLastReadBackSetpoint(), actual, until);
                overrideRepository.save(new HeatingManualOverride(actual, Source.HEAT_PUMP, now, until));
            }
            saveState(state, actual, actual, null, now);
            return;
        }

        // 4. Desired setpoint
        BigDecimal desired = override.map(HeatingManualOverride::getSetpoint)
                .orElseGet(() -> heatingSetpointService.scheduledSetpointAt(now));
        String reason = override.map(o -> "manual override (" + o.getSource() + ")").orElse("schedule");

        // 5. Already there? Compared with the read-back, so a heat pump that rounds what we write is not
        //    rewritten every run.
        if (state != null
                && sameSetpoint(desired, state.getLastDesiredSetpoint())
                && sameSetpoint(actual, state.getLastReadBackSetpoint())) {
            log.debug("Heating setpoint in sync: {} °C ({})", actual, reason);
            saveState(state, desired, actual, null, now); // updated_at doubles as "last successful check"
            return;
        }
        if (sameSetpoint(actual, desired)) {
            saveState(state, desired, actual, null, now);
            return;
        }

        // 6. Write. Record the attempt first: if the call fails after the heat pump applied the value,
        //    step 2 of the next run recognises it as ours instead of a manual change.
        BigDecimal previousDesired = state != null ? state.getLastDesiredSetpoint() : actual;
        BigDecimal previousReadBack = state != null ? state.getLastReadBackSetpoint() : actual;
        state = saveState(state, previousDesired, previousReadBack, desired, now);
        try {
            thermiaService.setComfortSetpoint(desired.doubleValue());
        } catch (InvalidValueException e) {
            log.error("Desired heating setpoint {} °C ({}) rejected, heat pump left at {} °C: {}",
                    desired, reason, actual, e.getMessage());
            saveState(state, previousDesired, previousReadBack, null, now);
            return;
        }

        BigDecimal readBack;
        try {
            readBack = readPumpSetpoint();
        } catch (ThermiaCommException e) {
            // Leave the write pending: the next run confirms it by reading the heat pump.
            log.warn("Wrote heating setpoint {} °C but could not read it back: {}", desired, e.getMessage());
            return;
        }
        saveState(state, desired, readBack, null, now);
        log.info("Heating setpoint {} -> {} °C ({}){}", actual, desired, reason,
                sameSetpoint(readBack, desired) ? "" : ", heat pump reports " + readBack + " °C");
    }

    // ---------------------------------------------------------------------------------------------
    // Manual override API

    /** The override in effect right now, if any. */
    public Optional<HeatingManualOverride> getActiveOverride() {
        Instant now = Instant.now(clock);
        return overrideRepository.findOpen().filter(o -> o.isActiveAt(now));
    }

    /**
     * Set a manual override through the API (replaces any open one) and apply it right away.
     *
     * @param hours how long to keep it; {@code null} = the configured default
     * @return the override as it is after being applied
     */
    public synchronized HeatingManualOverride startOverride(BigDecimal setpoint, Double hours) {
        Instant now = Instant.now(clock);
        Duration duration = hours != null ? hoursToDuration(hours) : overrideDuration();
        overrideRepository.findOpen().ifPresent(o -> {
            o.setEndedAt(now);
            overrideRepository.save(o);
        });
        HeatingManualOverride created = overrideRepository.save(
                new HeatingManualOverride(setpoint, Source.API, now, now.plus(duration)));
        log.info("Manual override {} °C set via API until {}", setpoint, created.getValidUntil());
        applyApiChange();
        return overrideRepository.findOpen().orElse(created);
    }

    /** Cancel the open override (if any) and return to the schedule right away. */
    public synchronized boolean cancelOverride() {
        Instant now = Instant.now(clock);
        Optional<HeatingManualOverride> open = overrideRepository.findOpen();
        if (open.isEmpty()) {
            return false;
        }
        open.get().setEndedAt(now);
        overrideRepository.save(open.get());
        log.info("Manual override {} °C cancelled via API", open.get().getSetpoint());
        applyApiChange();
        return true;
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Applies an override change now. The API call is newer than any change on the panel the loop has not
     * seen yet, so the current heat pump value is accepted as known first; otherwise reconcile() would take
     * it for a manual change and override what the API caller just asked for. If the heat pump is
     * unreachable, the scheduler applies the change on its next run.
     */
    private void applyApiChange() {
        try {
            BigDecimal actual = readPumpSetpoint();
            stateRepository.findById(HeatingControlState.SINGLETON_ID).ifPresent(s -> {
                if (s.getPendingSetpoint() == null || !sameSetpoint(actual, s.getPendingSetpoint())) {
                    s.setLastReadBackSetpoint(actual);
                    stateRepository.save(s);
                }
            });
            reconcile();
        } catch (ThermiaCommException e) {
            log.warn("Heat pump not reachable, override change will be applied on the next control run: {}",
                    e.getMessage());
        }
    }

    private BigDecimal readPumpSetpoint() {
        return scale(BigDecimal.valueOf(thermiaService.getComfortSetpoint()));
    }

    private HeatingControlState saveState(HeatingControlState existing, BigDecimal desired, BigDecimal readBack,
                                          BigDecimal pending, Instant now) {
        HeatingControlState s = existing != null ? existing : new HeatingControlState();
        s.setLastDesiredSetpoint(scale(desired));
        s.setLastReadBackSetpoint(scale(readBack));
        s.setPendingSetpoint(pending != null ? scale(pending) : null);
        s.setUpdatedAt(now);
        stateRepository.save(s);
        return s;
    }

    /**
     * End of an override detected at {@code now}. Counted from the start of the minute: control runs fire a
     * few milliseconds after :15 s, and without this an override expiring at e.g. 12:15:15.007 would be
     * missed by the 12:15:15.003 run and last 15 minutes longer.
     */
    static Instant overrideEnd(Instant now, Duration duration) {
        return now.truncatedTo(ChronoUnit.MINUTES).plus(duration);
    }

    Duration overrideDuration() {
        String value = configService.getConfigValue(OVERRIDE_HOURS_KEY, String.valueOf(DEFAULT_OVERRIDE_HOURS));
        try {
            return hoursToDuration(Double.parseDouble(value));
        } catch (NumberFormatException | InvalidValueException e) {
            log.warn("Invalid {} '{}', using {} h", OVERRIDE_HOURS_KEY, value, DEFAULT_OVERRIDE_HOURS);
            return hoursToDuration(DEFAULT_OVERRIDE_HOURS);
        }
    }

    private static Duration hoursToDuration(double hours) {
        long minutes = Math.round(hours * 60);
        if (!(hours > 0 && hours <= 24 * 7) || minutes < 1) {
            throw new InvalidValueException("Override duration must be between 1 minute and 168 hours, got: " + hours + " h");
        }
        return Duration.ofMinutes(minutes);
    }

    private static boolean sameSetpoint(BigDecimal a, BigDecimal b) {
        return a != null && b != null && scale(a).compareTo(scale(b)) == 0;
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
