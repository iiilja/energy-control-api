package com.ilja.smarthome.energycontrol.service;

import com.ilja.smarthome.energycontrol.domain.model.DefaultWeeklySchedule;
import com.ilja.smarthome.energycontrol.domain.model.HeatingSetpointSchedule;
import com.ilja.smarthome.energycontrol.domain.model.NordpoolPrice;
import com.ilja.smarthome.energycontrol.dto.heating.SetpointScheduleItemRequest;
import com.ilja.smarthome.energycontrol.dto.heating.SetpointScheduleItemResponse;
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
import java.util.List;
import java.util.Map;
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

        List<NordpoolPrice> prices = nordpoolPriceRepository
                .findByPriceTimestampBetweenOrderByPriceTimestampAsc(startOfDay, endOfDay);

        List<HeatingSetpointSchedule> schedules = scheduleRepository
                .findByPriceTimestampBetween(startOfDay, endOfDay);

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
                    .orElseThrow(() -> new IllegalArgumentException(
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

        List<NordpoolPrice> prices = nordpoolPriceRepository
                .findByPriceTimestampBetweenOrderByPriceTimestampAsc(startOfDay, endOfDay);

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
            LocalTime priceTime = price.getPriceTimestamp().toLocalTime();
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
            if (time.isAfter(entry.getStartTime())) {
                applicable = entry;
            } else {
                break;
            }
        }

        return applicable != null ? applicable.getSetpoint() : getDefaultSetpoint();
    }

    private BigDecimal getDefaultSetpoint() {
        return new BigDecimal(configService.getConfigValue("heating.setpoint.default", "21.0"));
    }
}
