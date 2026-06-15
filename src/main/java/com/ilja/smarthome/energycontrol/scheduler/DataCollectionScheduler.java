package com.ilja.smarthome.energycontrol.scheduler;

import com.ilja.smarthome.energycontrol.domain.model.HeatPumpReading;
import com.ilja.smarthome.energycontrol.service.ConfigurationService;
import com.ilja.smarthome.energycontrol.service.DataCollectionService;
import com.ilja.smarthome.energycontrol.thermia.exception.ThermiaCommException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler for automatic heat pump data collection.
 * Collects data from Thermia heat pump at regular intervals via Modbus TCP.
 */
@Component
@Slf4j
public class DataCollectionScheduler {

    private final DataCollectionService dataCollectionService;
    private final ConfigurationService configService;

    @Autowired
    public DataCollectionScheduler(
            DataCollectionService dataCollectionService,
            ConfigurationService configService) {
        this.dataCollectionService = dataCollectionService;
        this.configService = configService;
    }

    /**
     * Scheduled task to collect data from Thermia heat pump.
     * Runs at fixed delay (not fixed rate) to prevent overlapping executions.
     * Interval is configured via collection.interval.ms property (default 60000ms = 1 minute).
     */
    @Scheduled(fixedDelayString = "${collection.interval.ms:60000}")
    public void collectData() {
        // Check if collection is enabled
        boolean enabled = Boolean.parseBoolean(
                configService.getConfigValue("collection.enabled", "true")
        );

        if (!enabled) {
            log.debug("Data collection is disabled via configuration");
            return;
        }

        try {
            log.info("Starting scheduled data collection");
            HeatPumpReading reading = dataCollectionService.collectAndStoreData();
            log.info("Scheduled collection completed successfully. Reading ID: {}, Outdoor Temp: {}°C",
                    reading.getId(),
                    reading.getTemperatures() != null ? reading.getTemperatures().getOutdoorTemp() : "N/A");

        } catch (ThermiaCommException e) {
            log.warn("Thermia communication failed during scheduled collection: {}",
                    e.getMessage());
            // Don't rethrow - let scheduler continue on next iteration

        } catch (Exception e) {
            log.error("Unexpected error during scheduled data collection", e);
            // Don't rethrow - let scheduler continue on next iteration
        }
    }
}
