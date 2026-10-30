package com.ilja.smarthome.energycontrol.service;

import com.ilja.smarthome.energycontrol.exception.InvalidValueException;
import com.ilja.smarthome.energycontrol.domain.model.DefaultWeeklySchedule;
import com.ilja.smarthome.energycontrol.domain.model.HeatingSetpointSchedule;
import com.ilja.smarthome.energycontrol.domain.model.NordpoolPrice;
import com.ilja.smarthome.energycontrol.dto.heating.SetpointScheduleItemRequest;
import com.ilja.smarthome.energycontrol.dto.heating.SetpointScheduleItemResponse;
import com.ilja.smarthome.energycontrol.dto.heating.WeeklyScheduleEntryDto;
import com.ilja.smarthome.energycontrol.dto.heating.WeeklyScheduleEntryRequest;
import com.ilja.smarthome.energycontrol.repository.DefaultWeeklyScheduleRepository;
import com.ilja.smarthome.energycontrol.repository.HeatingSetpointScheduleRepository;
import com.ilja.smarthome.energycontrol.repository.NordpoolPriceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class HeatingSetpointService {

    private static final ZoneId EUROPE_TALLINN = ZoneId.of("Europe/Tallinn");
    /** Nordpool price slots are 15 minutes; Tallinn offsets are whole hours, so slots align with UTC. */
    private static final long SLOT_SECONDS = 15 * 60;

    private final HeatingSetpointScheduleRepository scheduleRepository;
    private final NordpoolPriceRepository nordpoolPriceRepository;
    private final DefaultWeeklyScheduleRepository weeklyScheduleRepository;
    private final ConfigurationService configService;

    @Autowired
    public HeatingSetpointService(
            HeatingSetpointScheduleRepository scheduleRepository,
            NordpoolPriceRepository nordpoolPriceRepository,
            DefaultWeeklyScheduleRepository weeklyScheduleRepository,
            ConfigurationService configService) {
        this.scheduleRepository = scheduleRepository;
        this.nordpoolPriceRepository = nordpoolPriceRepository;
        this.weeklyScheduleRepository = weeklyScheduleRepository;
        this.configService = configService;
    }

    /**
     * Scheduled setpoint for every price slot of a Tallinn day: the slot override if there is one,
     * otherwise the weekly template (otherwise the default). Manual overrides are not included.
     */
    public List<SetpointScheduleItemResponse> getScheduleForDate(LocalDate date) {
        ZonedDateTime startOfDay = date.atStartOfDay(EUROPE_TALLINN);
        ZonedDateTime endOfDay = startOfDay.plusDays(1);

        List<NordpoolPrice> prices = nordpoolPriceRepository.findInPeriod(startOfDay, endOfDay);

        Map<Long, BigDecimal> slotOverrides = scheduleRepository.findInPeriod(startOfDay, endOfDay).stream()
                .collect(Collectors.toMap(
                        s -> s.getNordpoolPrice().getId(),
                        HeatingSetpointSchedule::getTargetSetpoint
                ));

        List<DefaultWeeklySchedule> template = templateFor(date.getDayOfWeek());

        return prices.stream()
                .map(price -> new SetpointScheduleItemResponse(
                        price.getPriceTimestamp(),
                        price.getPrice(),
                        slotOverrides.getOrDefault(price.getId(), findSetpointForTime(template,
                                price.getPriceTimestamp().withZoneSameInstant(EUROPE_TALLINN).toLocalTime())),
                        price.getId()
                ))
                .collect(Collectors.toList());
    }

    /**
     * The setpoint the schedule asks for at {@code instant}: the override for the 15-minute price slot
     * containing it, otherwise the weekly template for that Tallinn day and time, otherwise the default.
     * Does not depend on prices being available. Manual overrides are handled by {@code HeatingControlService}.
     */
    public BigDecimal scheduledSetpointAt(Instant instant) {
        ZonedDateTime slotStart = Instant.ofEpochSecond(Math.floorDiv(instant.getEpochSecond(), SLOT_SECONDS) * SLOT_SECONDS)
                .atZone(EUROPE_TALLINN);
        Optional<BigDecimal> slotOverride = scheduleRepository
                .findInPeriod(slotStart, slotStart.plusSeconds(SLOT_SECONDS)).stream()
                .findFirst()
                .map(HeatingSetpointSchedule::getTargetSetpoint);
        if (slotOverride.isPresent()) {
            return slotOverride.get();
        }

        ZonedDateTime local = instant.atZone(EUROPE_TALLINN);
        return findSetpointForTime(templateFor(local.getDayOfWeek()), local.toLocalTime());
    }

    /** Saves setpoint overrides for single price slots. */
    @Transactional
    public List<HeatingSetpointSchedule> saveSchedules(List<SetpointScheduleItemRequest> requests) {
        List<HeatingSetpointSchedule> schedules = new ArrayList<>();

        for (SetpointScheduleItemRequest request : requests) {
            NordpoolPrice price = nordpoolPriceRepository.findById(request.getNordpoolPriceId())
                    .orElseThrow(() -> new InvalidValueException(
                            "Nordpool price not found: " + request.getNordpoolPriceId()));

            HeatingSetpointSchedule schedule = scheduleRepository.findByNordpoolPrice(price)
                    .orElse(new HeatingSetpointSchedule(price, request.getSetpoint()));

            schedule.setTargetSetpoint(request.getSetpoint());
            schedules.add(schedule);
        }

        List<HeatingSetpointSchedule> saved = scheduleRepository.saveAll(schedules);
        log.info("Saved {} heating setpoint slot overrides", saved.size());
        return saved;
    }

    private List<DefaultWeeklySchedule> templateFor(DayOfWeek dayOfWeek) {
        return weeklyScheduleRepository.findByDayOfWeekOrderByStartTimeAsc((short) dayOfWeek.getValue());
    }

    private BigDecimal findSetpointForTime(List<DefaultWeeklySchedule> template, LocalTime time) {
        DefaultWeeklySchedule applicable = null;

        for (DefaultWeeklySchedule entry : template) {
            // An entry applies from its start time inclusive: the 05:00 entry covers the 05:00 slot.
            if (!time.isBefore(entry.getStartTime())) {
                applicable = entry;
            } else {
                break;
            }
        }

        return applicable != null ? applicable.getSetpoint() : getDefaultSetpoint();
    }

    public List<WeeklyScheduleEntryDto> getWeeklySchedule() {
        return weeklyScheduleRepository.findAllByOrderByDayOfWeekAscStartTimeAsc()
                .stream()
                .map(e -> new WeeklyScheduleEntryDto(
                        e.getId(),
                        e.getDayOfWeek(),
                        e.getStartTime().toString(),
                        e.getSetpoint()))
                .toList();
    }

    /**
     * Replaces the whole weekly template.
     *
     * @throws InvalidValueException if two entries share the same day and start time
     *                               ("05:00" and "05:00:00" count as the same time)
     */
    @Transactional
    public void saveWeeklySchedule(List<WeeklyScheduleEntryRequest> requests) {
        List<DefaultWeeklySchedule> entries = requests.stream()
                .map(r -> new DefaultWeeklySchedule(r.dayOfWeek(), LocalTime.parse(r.startTime()), r.setpoint()))
                .toList();

        Set<String> seen = new HashSet<>();
        for (DefaultWeeklySchedule e : entries) {
            if (!seen.add(e.getDayOfWeek() + "@" + e.getStartTime())) {
                throw new InvalidValueException("Duplicate weekly schedule entry: day " + e.getDayOfWeek()
                        + " at " + e.getStartTime());
            }
        }

        // Bulk delete runs immediately. deleteAll() would only queue the deletes until flush, while
        // IDENTITY inserts run at once, so re-saving an existing (day, time) would hit unique_day_time.
        weeklyScheduleRepository.deleteAllInBatch();
        weeklyScheduleRepository.saveAll(entries);
        log.info("Saved weekly schedule with {} entries", entries.size());
    }

    private BigDecimal getDefaultSetpoint() {
        return new BigDecimal(configService.getConfigValue("heating.setpoint.default", "21.0"));
    }
}
