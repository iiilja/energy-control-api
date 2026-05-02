package com.ilja.smarthome.energycontrol.scheduler;

import com.ilja.smarthome.energycontrol.domain.model.HeatPumpReading;
import com.ilja.smarthome.energycontrol.exception.ESP32CommunicationException;
import com.ilja.smarthome.energycontrol.service.ConfigurationService;
import com.ilja.smarthome.energycontrol.service.DataCollectionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler for automatic heat pump data collection.
 * Collects data from ESP32 at regular intervals.
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
     * Scheduled task to collect data from ESP32.
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

        } catch (ESP32CommunicationException e) {
            log.warn("ESP32 communication failed during scheduled collection: {}",
                    e.getMessage());
            // Don't rethrow - let scheduler continue on next iteration

        } catch (Exception e) {
            log.error("Unexpected error during scheduled data collection", e);
            // Don't rethrow - let scheduler continue on next iteration
        }
    }
}
