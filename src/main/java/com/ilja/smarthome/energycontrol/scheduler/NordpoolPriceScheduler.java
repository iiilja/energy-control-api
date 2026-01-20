package com.ilja.smarthome.energycontrol.scheduler;

import com.ilja.smarthome.energycontrol.exception.EleringApiException;
import com.ilja.smarthome.energycontrol.service.ConfigurationService;
import com.ilja.smarthome.energycontrol.service.HeatingSetpointService;
import com.ilja.smarthome.energycontrol.service.NordpoolPriceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

@Component
@Slf4j
public class NordpoolPriceScheduler {

    private static final ZoneId EUROPE_TALLINN = ZoneId.of("Europe/Tallinn");

    private final NordpoolPriceService nordpoolPriceService;
    private final HeatingSetpointService heatingSetpointService;
    private final ConfigurationService configService;

    @Autowired
    public NordpoolPriceScheduler(
            NordpoolPriceService nordpoolPriceService,
            HeatingSetpointService heatingSetpointService,
            ConfigurationService configService) {
        this.nordpoolPriceService = nordpoolPriceService;
        this.heatingSetpointService = heatingSetpointService;
        this.configService = configService;
    }

    @Scheduled(cron = "${nordpool.fetch.cron:0 22 00 * * *}")
    public void fetchDailyPrices() {
        boolean enabled = Boolean.parseBoolean(
                configService.getConfigValue("nordpool.fetch.enabled", "true")
        );

        if (!enabled) {
            log.debug("Nordpool price fetching is disabled via configuration");
            return;
        }

        try {
            log.info("Starting scheduled Nordpool price fetch");
            int pricesStored = nordpoolPriceService.fetchAndStorePrices();
            log.info("Scheduled Nordpool price fetch completed. Stored {} new price entries", pricesStored);

            if (pricesStored > 0) {
                LocalDate tomorrow = LocalDate.now(EUROPE_TALLINN).plusDays(1);
                log.info("Applying weekly template to tomorrow's date: {}", tomorrow);
                int schedulesCreated = heatingSetpointService.applyWeeklyTemplateToDate(tomorrow);
                log.info("Applied weekly template. Created {} heating setpoint schedules for {}", schedulesCreated, tomorrow);
            }

        } catch (EleringApiException e) {
            log.warn("Elering API communication failed during scheduled fetch: {}", e.getMessage());

        } catch (Exception e) {
            log.error("Unexpected error during scheduled Nordpool price fetch", e);
        }
    }

    @Scheduled(cron = "${nordpool.cleanup.cron:0 0 2 * * *}")
    public void cleanupOldPrices() {
        boolean enabled = Boolean.parseBoolean(
                configService.getConfigValue("nordpool.cleanup.enabled", "true")
        );

        if (!enabled) {
            log.debug("Nordpool price cleanup is disabled via configuration");
            return;
        }

        try {
            int daysToKeep = Integer.parseInt(
                    configService.getConfigValue("nordpool.cleanup.days_to_keep", "30")
            );

            log.info("Starting scheduled Nordpool price cleanup (keeping last {} days)", daysToKeep);
            nordpoolPriceService.cleanupOldPrices(daysToKeep);
            log.info("Scheduled Nordpool price cleanup completed");

        } catch (Exception e) {
            log.error("Unexpected error during scheduled Nordpool price cleanup", e);
        }
    }
}
