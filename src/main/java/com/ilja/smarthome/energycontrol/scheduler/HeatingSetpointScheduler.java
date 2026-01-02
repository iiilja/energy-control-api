package com.ilja.smarthome.energycontrol.scheduler;

import com.ilja.smarthome.energycontrol.domain.model.HeatingSetpointSchedule;
import com.ilja.smarthome.energycontrol.exception.ESP32CommunicationException;
import com.ilja.smarthome.energycontrol.repository.HeatingSetpointScheduleRepository;
import com.ilja.smarthome.energycontrol.service.ConfigurationService;
import com.ilja.smarthome.energycontrol.service.ESP32ClientService;
import com.ilja.smarthome.energycontrol.service.HeatingSetpointService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Optional;

@Component
@Slf4j
public class HeatingSetpointScheduler {
    private final ESP32ClientService esp32Client;
    private final HeatingSetpointService heatingSetpointService;
    private final ConfigurationService configService;
    private final HeatingSetpointScheduleRepository scheduleRepository;

    @Autowired
    public HeatingSetpointScheduler(
            ESP32ClientService esp32Client,
            HeatingSetpointService heatingSetpointService,
            ConfigurationService configService,
            HeatingSetpointScheduleRepository scheduleRepository) {
        this.esp32Client = esp32Client;
        this.heatingSetpointService = heatingSetpointService;
        this.configService = configService;
        this.scheduleRepository = scheduleRepository;
    }

    @Scheduled(cron = "${heating.setpoint.adjustment.cron:0 0 * * * *}")
    public void applyHourlySetpoint() {
        boolean enabled = Boolean.parseBoolean(
                configService.getConfigValue("heating.setpoint.adjustment.enabled", "true")
        );

        if (!enabled) {
            log.debug("Heating setpoint adjustment is disabled via configuration");
            return;
        }

        try {
            log.info("Starting scheduled heating setpoint adjustment");
            ZonedDateTime now = ZonedDateTime.now();

            Optional<HeatingSetpointSchedule> scheduleOpt = scheduleRepository.findNextPendingSchedule(now);

            if (scheduleOpt.isEmpty()) {
                log.debug("No pending heating setpoint schedule found for current time");
                return;
            }

            HeatingSetpointSchedule schedule = scheduleOpt.get();
            BigDecimal targetSetpoint = schedule.getTargetSetpoint();

            try {
                esp32Client.setHeatingSetpoint(targetSetpoint.doubleValue());
                schedule.setApplied(true);
                scheduleRepository.save(schedule);

                log.info("Successfully applied scheduled heating setpoint: {} -> {}°C (price: {} EUR/MWh)",
                        getDefaultSetpoint(), targetSetpoint, schedule.getNordpoolPrice().getPrice());

            } catch (ESP32CommunicationException e) {
                log.error("Failed to apply scheduled heating setpoint: {}", e.getMessage());
            }
            log.info("Scheduled heating setpoint adjustment completed");

        } catch (Exception e) {
            log.error("Unexpected error during scheduled heating setpoint adjustment", e);
        }
    }

    public void triggerManualAdjustment() {
        log.info("Manual heating setpoint adjustment triggered");
        heatingSetpointService.applyScheduledSetpoint();
    }

    private BigDecimal getDefaultSetpoint() {
        return new BigDecimal(configService.getConfigValue("heating.setpoint.default", "21.0"));
    }
}
