package com.ilja.smarthome.energycontrol.scheduler;

import com.ilja.smarthome.energycontrol.service.ConfigurationService;
import com.ilja.smarthome.energycontrol.service.HeatingControlService;
import com.ilja.smarthome.energycontrol.thermia.exception.ThermiaCommException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the heating control loop every 15 minutes (15 s after each slot boundary, so the new
 * price slot has started). See {@link HeatingControlService#reconcile()}.
 */
@Component
@Slf4j
public class HeatingSetpointScheduler {

    private final HeatingControlService heatingControlService;
    private final ConfigurationService configService;

    public HeatingSetpointScheduler(HeatingControlService heatingControlService, ConfigurationService configService) {
        this.heatingControlService = heatingControlService;
        this.configService = configService;
    }

    @Scheduled(cron = "${heating.setpoint.adjustment.cron:15 0/15 * * * *}")
    public void controlSetpoint() {
        boolean enabled = Boolean.parseBoolean(
                configService.getConfigValue("heating.setpoint.adjustment.enabled", "true"));
        if (!enabled) {
            log.debug("Heating setpoint control is disabled via configuration");
            return;
        }

        try {
            heatingControlService.reconcile();
        } catch (ThermiaCommException e) {
            log.warn("Heat pump not reachable, setpoint control retried next run: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error during heating setpoint control", e);
        }
    }
}
