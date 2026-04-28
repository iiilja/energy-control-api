package com.ilja.smarthome.energycontrol.scheduler;

import com.ilja.smarthome.energycontrol.domain.model.HeatingSetpointSchedule;
import com.ilja.smarthome.energycontrol.thermia.exception.ThermiaCommException;
import com.ilja.smarthome.energycontrol.thermia.service.ThermiaHeatPumpService;
import com.ilja.smarthome.energycontrol.repository.HeatingSetpointScheduleRepository;
import com.ilja.smarthome.energycontrol.service.ConfigurationService;
import com.ilja.smarthome.energycontrol.service.HeatingSetpointService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Component
@Slf4j
public class HeatingSetpointScheduler {
    private final ThermiaHeatPumpService thermiaService;
    private final HeatingSetpointService heatingSetpointService;
    private final ConfigurationService configService;
    private final HeatingSetpointScheduleRepository scheduleRepository;

    @Autowired
    public HeatingSetpointScheduler(
            ThermiaHeatPumpService thermiaService,
            HeatingSetpointService heatingSetpointService,
            ConfigurationService configService,
            HeatingSetpointScheduleRepository scheduleRepository) {
        this.thermiaService = thermiaService;
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
            ZonedDateTime now = ZonedDateTime.now().truncatedTo(ChronoUnit.HOURS);

            Optional<HeatingSetpointSchedule> scheduleOpt = scheduleRepository.findNextPendingSchedule(now);

            if (scheduleOpt.isEmpty()) {
                log.debug("No pending heating setpoint schedule found for current time");
                return;
            }

            HeatingSetpointSchedule schedule = scheduleOpt.get();
            BigDecimal targetSetpoint = schedule.getTargetSetpoint();

            try {
                thermiaService.setComfortSetpoint(targetSetpoint.doubleValue());
                schedule.setApplied(true);
                scheduleRepository.save(schedule);

                log.info("Successfully applied scheduled heating setpoint: {} -> {}°C (price: {} EUR/MWh)",
                            getDefaultSetpoint(), targetSetpoint, schedule.getNordpoolPrice().getPrice());

            } catch (ThermiaCommException e) {
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
