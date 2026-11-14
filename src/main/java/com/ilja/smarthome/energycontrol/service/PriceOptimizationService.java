package com.ilja.smarthome.energycontrol.service;

import com.ilja.smarthome.energycontrol.domain.model.HeatingSetpointSchedule;
import com.ilja.smarthome.energycontrol.domain.model.NordpoolPrice;
import com.ilja.smarthome.energycontrol.dto.heating.PricePlanResponse;
import com.ilja.smarthome.energycontrol.repository.HeatingSetpointScheduleRepository;
import com.ilja.smarthome.energycontrol.repository.NordpoolPriceRepository;
import com.ilja.smarthome.energycontrol.service.PricePeakPlanner.Kind;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Price optimisation of the heating setpoint: cut the price peaks of a day and compensate next to them.
 * In peak slots the setpoint is lowered below the weekly template, in the compensation slots before
 * (or after) a peak raised above it. See {@link PricePeakPlanner} for how peaks are found.
 *
 * <p>The plan is written as price-slot overrides ({@code heating_setpoint_schedule}, source PRICE),
 * which the 15-minute control loop already follows; a manual override still wins over it. Each run
 * re-plans the slots that have not started yet, so changes to the settings or the weekly template
 * take effect on the next run. Slots set by hand (source MANUAL) are never touched, and slots that
 * have started are kept as they were. When the optimisation is switched off, its future slots are removed.
 *
 * <p>Settings ({@code system_configuration}, read on every run): {@code heating.price_optimization.enabled},
 * {@code .peak_threshold} (EUR/MWh above the median), {@code .lower}, {@code .raise} (°C),
 * {@code .compensation_minutes} (a multiple of 15). A weekly template change re-plans at once
 * ({@link com.ilja.smarthome.energycontrol.scheduler.PriceOptimizationScheduler}); a settings change
 * takes effect with the next hourly run.
 */
@Service
@Slf4j
public class PriceOptimizationService {

    static final String PREFIX = "heating.price_optimization.";

    private static final ZoneId EUROPE_TALLINN = ZoneId.of("Europe/Tallinn");
    private static final Duration SLOT_LENGTH = Duration.ofMinutes(15);
    private static final BigDecimal MIN_SETPOINT = new BigDecimal("15");
    private static final BigDecimal MAX_SETPOINT = new BigDecimal("30");
    private static final BigDecimal MAX_SHIFT = new BigDecimal("3");
    private static final int MAX_COMPENSATION_MINUTES = 240;

    /**
     * @param enabled switched on and all values valid; the values are also used (for the UI) when it is off
     * @param valid   false if a value was invalid (then the defaults are used for the UI view)
     */
    record Settings(boolean enabled, boolean valid, BigDecimal peakThreshold, BigDecimal lower, BigDecimal raise,
                    int compensationMinutes) {
        static final Settings DEFAULTS = new Settings(false, true, new BigDecimal("50"), new BigDecimal("1.0"),
                new BigDecimal("1.0"), 60);
        static final Settings INVALID = new Settings(false, false, DEFAULTS.peakThreshold(), DEFAULTS.lower(),
                DEFAULTS.raise(), DEFAULTS.compensationMinutes());

        int compensationSlots() {
            return compensationMinutes / (int) SLOT_LENGTH.toMinutes();
        }
    }

    /** One day's prices with their kinds and template setpoints, all aligned by index. */
    private record Analysis(List<NordpoolPrice> prices, boolean complete, PricePeakPlanner.Result result,
                            List<BigDecimal> templates) {
    }

    private final NordpoolPriceRepository priceRepository;
    private final HeatingSetpointScheduleRepository scheduleRepository;
    private final HeatingSetpointService setpointService;
    private final ConfigurationService configService;
    private final Clock clock;

    public PriceOptimizationService(NordpoolPriceRepository priceRepository,
                                    HeatingSetpointScheduleRepository scheduleRepository,
                                    HeatingSetpointService setpointService,
                                    ConfigurationService configService,
                                    Clock clock) {
        this.priceRepository = priceRepository;
        this.scheduleRepository = scheduleRepository;
        this.setpointService = setpointService;
        this.configService = configService;
        this.clock = clock;
    }

    /**
     * Re-plans the slots of a Tallinn day that start after now.
     *
     * @return number of slots whose planned setpoint changed (0 if the stored plan was already right)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW) // own transaction also when called after a commit
    public int optimize(LocalDate day) {
        Settings settings = readSettings();
        ZonedDateTime start = day.atStartOfDay(EUROPE_TALLINN);
        Instant now = clock.instant();

        List<HeatingSetpointSchedule> existing = scheduleRepository.findInPeriod(start, start.plusDays(1));
        List<HeatingSetpointSchedule> ownFuture = existing.stream()
                .filter(s -> s.getSource() == HeatingSetpointSchedule.Source.PRICE)
                .filter(s -> startsAfter(s.getNordpoolPrice(), now))
                .toList();
        Set<Long> manualSlots = existing.stream()
                .filter(s -> s.getSource() == HeatingSetpointSchedule.Source.MANUAL)
                .map(s -> s.getNordpoolPrice().getId())
                .collect(Collectors.toSet());

        Map<NordpoolPrice, Planned> plan = settings.enabled()
                ? plan(day, analyse(day, settings), now, manualSlots, settings)
                : Map.of();

        Map<Long, BigDecimal> stored = ownFuture.stream().collect(Collectors.toMap(
                s -> s.getNordpoolPrice().getId(), HeatingSetpointSchedule::getTargetSetpoint));
        Map<Long, BigDecimal> planned = plan.entrySet().stream().collect(Collectors.toMap(
                e -> e.getKey().getId(), e -> e.getValue().target()));
        if (sameTargets(stored, planned)) {
            log.debug("Price plan for {} unchanged ({} slots)", day, planned.size());
            return 0;
        }

        // Bulk delete runs at once, so the new rows for the same slots do not hit unique_price_schedule.
        // Only rows that are still PRICE: one turned into a manual override meanwhile is kept.
        if (!ownFuture.isEmpty()) {
            scheduleRepository.deleteByIdInAndSource(
                    ownFuture.stream().map(HeatingSetpointSchedule::getId).toList(), HeatingSetpointSchedule.Source.PRICE);
        }
        List<HeatingSetpointSchedule> rows = new ArrayList<>();
        plan.forEach((price, slot) -> rows.add(
                new HeatingSetpointSchedule(price, slot.target(), HeatingSetpointSchedule.Source.PRICE)));
        scheduleRepository.saveAll(rows);

        logPlan(day, plan, ownFuture.size(), settings);
        return changedSlots(stored, planned);
    }

    /** The day as the optimisation sees it, for the UI chart. Read-only; works when switched off too. */
    @Transactional(readOnly = true)
    public PricePlanResponse describe(LocalDate day) {
        Settings settings = readSettings();
        ZonedDateTime start = day.atStartOfDay(EUROPE_TALLINN);
        Analysis analysis = analyse(day, settings);
        Map<Long, HeatingSetpointSchedule> overrides = scheduleRepository.findInPeriod(start, start.plusDays(1))
                .stream()
                .collect(Collectors.toMap(s -> s.getNordpoolPrice().getId(), Function.identity()));

        List<PricePlanResponse.Slot> slots = new ArrayList<>();
        for (int i = 0; i < analysis.prices().size(); i++) {
            NordpoolPrice price = analysis.prices().get(i);
            Kind kind = analysis.result().kinds().get(i);
            BigDecimal template = analysis.templates().get(i);
            HeatingSetpointSchedule override = overrides.get(price.getId());
            slots.add(new PricePlanResponse.Slot(
                    price.getPriceTimestamp().withZoneSameInstant(EUROPE_TALLINN).toOffsetDateTime().toString(),
                    price.getPrice(),
                    kind.name(),
                    template,
                    target(kind, template, settings),
                    override != null ? override.getTargetSetpoint() : template,
                    override != null ? override.getSource().name() : "TEMPLATE"));
        }
        return new PricePlanResponse(day, settings.enabled(), settings.valid(), analysis.complete(),
                analysis.result().median(), analysis.result().threshold(),
                settings.peakThreshold(), settings.lower(), settings.raise(), settings.compensationMinutes(),
                slots);
    }

    // ---------------------------------------------------------------------------------------------

    private record Planned(BigDecimal target, Kind kind) {
    }

    private Analysis analyse(LocalDate day, Settings settings) {
        ZonedDateTime start = day.atStartOfDay(EUROPE_TALLINN);
        List<NordpoolPrice> prices = priceRepository.findInPeriod(start, start.plusDays(1));
        long expectedSlots = Duration.between(start, start.plusDays(1)).dividedBy(SLOT_LENGTH);
        boolean complete = prices.size() >= expectedSlots;
        // The median is only meaningful for the whole day
        PricePeakPlanner.Result result = complete
                ? PricePeakPlanner.plan(prices, settings.peakThreshold(), settings.compensationSlots())
                : new PricePeakPlanner.Result(null, null, new ArrayList<>(Collections.nCopies(prices.size(), Kind.NORMAL)));
        List<BigDecimal> templates = setpointService.templateSetpointsAt(
                prices.stream().map(p -> p.getPriceTimestamp().toInstant()).toList());
        return new Analysis(prices, complete, result, templates);
    }

    /** Target for every future, not manually set peak or compensation slot whose target differs from the template. */
    private Map<NordpoolPrice, Planned> plan(LocalDate day, Analysis analysis, Instant now, Set<Long> manualSlots,
                                             Settings settings) {
        if (!analysis.complete()) {
            log.debug("Price plan for {} skipped: {} prices stored, the day is not complete",
                    day, analysis.prices().size());
            return Map.of();
        }
        Map<NordpoolPrice, Planned> plan = new HashMap<>();
        for (int i = 0; i < analysis.prices().size(); i++) {
            NordpoolPrice price = analysis.prices().get(i);
            Kind kind = analysis.result().kinds().get(i);
            if (kind == Kind.NORMAL || !startsAfter(price, now) || manualSlots.contains(price.getId())) {
                continue;
            }
            BigDecimal template = analysis.templates().get(i);
            BigDecimal target = target(kind, template, settings);
            if (target.compareTo(template) != 0) {
                plan.put(price, new Planned(target, kind));
            }
        }
        return plan;
    }

    /**
     * Template shifted for the kind. Clamped only in the direction of the shift: a template already
     * outside 15-30 °C is never moved the wrong way (an "away" template of 12 °C stays 12 in a peak).
     */
    private static BigDecimal target(Kind kind, BigDecimal template, Settings settings) {
        BigDecimal target = switch (kind) {
            case PEAK -> template.subtract(settings.lower()).max(MIN_SETPOINT.min(template));
            case COMPENSATION -> template.add(settings.raise()).min(MAX_SETPOINT.max(template));
            case NORMAL -> template;
        };
        return target.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Current settings. Invalid values switch the optimisation off (with a warning) rather than guess;
     * the defaults are then used for the UI view.
     */
    Settings readSettings() {
        String enabledValue = config("enabled", "false").trim();
        boolean enabled = enabledValue.equalsIgnoreCase("true");
        if (!enabled && !enabledValue.equalsIgnoreCase("false")) {
            log.warn("{}enabled = '{}' is not true/false, optimisation is off", PREFIX, enabledValue);
        }
        try {
            Settings settings = new Settings(enabled, true,
                    new BigDecimal(config("peak_threshold", "50").trim()),
                    new BigDecimal(config("lower", "1.0").trim()),
                    new BigDecimal(config("raise", "1.0").trim()),
                    Integer.parseInt(config("compensation_minutes", "60").trim()));
            // A threshold of 0 would make at least half of every day a peak
            if (settings.peakThreshold().signum() <= 0
                    || !inRange(settings.lower()) || !inRange(settings.raise())
                    || settings.compensationMinutes() < 0 || settings.compensationMinutes() > MAX_COMPENSATION_MINUTES
                    || settings.compensationMinutes() % SLOT_LENGTH.toMinutes() != 0) {
                throw new IllegalArgumentException("out of range: " + settings);
            }
            return settings;
        } catch (IllegalArgumentException e) { // includes NumberFormatException
            log.warn("Price optimisation settings invalid ({}), optimisation is off until they are fixed",
                    e.getMessage());
            return Settings.INVALID;
        }
    }

    private String config(String key, String defaultValue) {
        return configService.getConfigValue(PREFIX + key, defaultValue);
    }

    private static boolean inRange(BigDecimal shift) {
        return shift.signum() >= 0 && shift.compareTo(MAX_SHIFT) <= 0;
    }

    private static boolean startsAfter(NordpoolPrice price, Instant now) {
        return price.getPriceTimestamp().toInstant().isAfter(now);
    }

    private static boolean sameTargets(Map<Long, BigDecimal> a, Map<Long, BigDecimal> b) {
        return a.size() == b.size() && a.entrySet().stream()
                .allMatch(e -> b.containsKey(e.getKey()) && b.get(e.getKey()).compareTo(e.getValue()) == 0);
    }

    private static int changedSlots(Map<Long, BigDecimal> stored, Map<Long, BigDecimal> planned) {
        Set<Long> slots = new HashSet<>(stored.keySet());
        slots.addAll(planned.keySet());
        return (int) slots.stream()
                .filter(id -> !Objects.equals(
                        stored.get(id) == null ? null : stored.get(id).stripTrailingZeros(),
                        planned.get(id) == null ? null : planned.get(id).stripTrailingZeros()))
                .count();
    }

    private void logPlan(LocalDate day, Map<NordpoolPrice, Planned> plan, int removed, Settings settings) {
        if (plan.isEmpty()) {
            log.info("Price plan for {}: no changes to the template{} (removed {} planned slots)", day,
                    settings.enabled() ? "" : " (optimisation off)", removed);
            return;
        }
        Set<String> peaks = new TreeSet<>();
        Set<String> compensation = new TreeSet<>();
        plan.forEach((price, slot) -> {
            String time = price.getPriceTimestamp().withZoneSameInstant(EUROPE_TALLINN).toLocalTime().toString();
            (slot.kind() == Kind.PEAK ? peaks : compensation).add(time);
        });
        log.info("Price plan for {}: lowered by {} °C at {}, raised by {} °C at {} ({} slots)",
                day, settings.lower(), peaks.isEmpty() ? "-" : String.join(",", peaks),
                settings.raise(), compensation.isEmpty() ? "-" : String.join(",", compensation), plan.size());
    }
}
