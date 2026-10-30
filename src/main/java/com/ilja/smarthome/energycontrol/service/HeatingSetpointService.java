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
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class HeatingSetpointService {

    private static final ZoneId EUROPE_TALLINN = ZoneId.of("Europe/Tallinn");

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

    public List<SetpointScheduleItemResponse> getScheduleForDate(LocalDate date) {
        ZonedDateTime startOfDay = date.atStartOfDay(EUROPE_TALLINN);
        ZonedDateTime endOfDay = startOfDay.plusDays(1);

        List<NordpoolPrice> prices = nordpoolPriceRepository.findInPeriod(startOfDay, endOfDay);

        List<HeatingSetpointSchedule> schedules = scheduleRepository.findInPeriod(startOfDay, endOfDay);

        Map<Long, BigDecimal> scheduleMap = schedules.stream()
                .collect(Collectors.toMap(
                        s -> s.getNordpoolPrice().getId(),
                        HeatingSetpointSchedule::getTargetSetpoint
                ));

        BigDecimal defaultSetpoint = getDefaultSetpoint();

        return prices.stream()
                .map(price -> new SetpointScheduleItemResponse(
                        price.getPriceTimestamp(),
                        price.getPrice(),
                        scheduleMap.getOrDefault(price.getId(), defaultSetpoint),
                        price.getId()
                ))
                .collect(Collectors.toList());
    }

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
            schedule.setApplied(false);
            schedules.add(schedule);
        }

        List<HeatingSetpointSchedule> saved = scheduleRepository.saveAll(schedules);
        log.info("Saved {} heating setpoint schedules", saved.size());
        return saved;
    }

    @Transactional
    public void applyScheduledSetpoint() {

    }

    @Transactional
    public List<HeatingSetpointSchedule> scheduleSetpoints(List<HeatingSetpointSchedule> schedules) {
        return scheduleRepository.saveAll(schedules);
    }

    public List<HeatingSetpointSchedule> getPendingSchedules() {
        return scheduleRepository.findByAppliedFalse();
    }

    @Transactional
    public int applyWeeklyTemplateToDate(LocalDate date) {
        ZonedDateTime startOfDay = date.atStartOfDay(EUROPE_TALLINN);
        ZonedDateTime endOfDay = startOfDay.plusDays(1);

        List<NordpoolPrice> prices = nordpoolPriceRepository.findInPeriod(startOfDay, endOfDay);

        if (prices.isEmpty()) {
            log.warn("No Nordpool prices found for date: {}", date);
            return 0;
        }

        DayOfWeek dayOfWeek = date.getDayOfWeek();
        short dayOfWeekValue = (short) dayOfWeek.getValue();

        List<DefaultWeeklySchedule> weeklyTemplate = weeklyScheduleRepository
                .findByDayOfWeekOrderByStartTimeAsc(dayOfWeekValue);

        if (weeklyTemplate.isEmpty()) {
            log.warn("No weekly template found for day of week: {}", dayOfWeekValue);
            return 0;
        }

        List<HeatingSetpointSchedule> schedules = new ArrayList<>();

        for (NordpoolPrice price : prices) {
            // Timestamps loaded from the DB are not guaranteed to be in Tallinn time
            // (PostgreSQL timestamptz comes back normalised, typically UTC).
            LocalTime priceTime = price.getPriceTimestamp().withZoneSameInstant(EUROPE_TALLINN).toLocalTime();
            BigDecimal setpoint = findSetpointForTime(weeklyTemplate, priceTime);

            HeatingSetpointSchedule schedule = scheduleRepository.findByNordpoolPrice(price)
                    .orElse(new HeatingSetpointSchedule(price, setpoint));

            schedule.setTargetSetpoint(setpoint);
            schedule.setApplied(false);
            schedules.add(schedule);
        }

        List<HeatingSetpointSchedule> saved = scheduleRepository.saveAll(schedules);
        log.info("Applied weekly template to {} Nordpool price entries for date: {}", saved.size(), date);
        return saved.size();
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
