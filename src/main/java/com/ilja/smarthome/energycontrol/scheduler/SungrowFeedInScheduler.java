package com.ilja.smarthome.energycontrol.scheduler;

import com.ilja.smarthome.energycontrol.service.ConfigurationService;
import com.ilja.smarthome.energycontrol.service.NordpoolPriceService;
import com.ilja.smarthome.energycontrol.sungrow.exception.SungrowCommunicationException;
import com.ilja.smarthome.energycontrol.sungrow.service.SungrowInverterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@Slf4j
public class SungrowFeedInScheduler {

    private final SungrowInverterService sungrowService;
    private final NordpoolPriceService nordpoolPriceService;
    private final ConfigurationService configService;

    @Value("${feed-in-limit.price-threshold-eur-mwh:11.0}")
    private double priceThresholdEurMwh;

    @Value("${feed-in-limit.limit-kw:3.0}")
    private double limitKw;

    public SungrowFeedInScheduler(
            SungrowInverterService sungrowService,
            NordpoolPriceService nordpoolPriceService,
            ConfigurationService configService) {
        this.sungrowService = sungrowService;
        this.nordpoolPriceService = nordpoolPriceService;
        this.configService = configService;
    }

    @Scheduled(cron = "0 0/15 * * * *")
    public void applyFeedInLimit() {
        if (!isEnabled()) {
            log.debug("Sungrow feed-in limit scheduler is disabled");
            return;
        }

        var currentPrice = nordpoolPriceService.getCurrentPrice();
        if (currentPrice.isEmpty()) {
            log.warn("No current Nordpool price available — skipping feed-in limit adjustment");
            return;
        }

        BigDecimal price = currentPrice.get().getPrice();
        boolean shouldLimit = price.compareTo(BigDecimal.valueOf(priceThresholdEurMwh)) < 0;

        log.debug("Nordpool price: {} EUR/MWh, threshold: {} EUR/MWh, shouldLimit: {}",
                price, priceThresholdEurMwh, shouldLimit);

        try {
            boolean powerLimitEnabled = sungrowService.isPowerLimitEnabled();

            if (shouldLimit) {
                log.info("Price {} EUR/MWh is below threshold {} EUR/MWh",
                        price, priceThresholdEurMwh);
                if (!powerLimitEnabled) {
                    log.info("Enabling feed-in limit");
                    sungrowService.enablePowerLimit();
                } else {
                    log.info("Feed-in limit already enabled");
                }
                double powerLimitKw = sungrowService.getPowerLimitKw();

                if (powerLimitKw != limitKw) {
                    log.info("Adjusting feed-in limit from {} kW to {} kW", powerLimitKw, limitKw);
                    sungrowService.setPowerLimitKw(limitKw);
                } else {
                    log.debug("Feed-in limit already set to {} kW — no change needed", limitKw);
                }
            } else {
                if (powerLimitEnabled) {
                    log.info("Price {} EUR/MWh is at or above threshold {} EUR/MWh — disabling feed-in limit",
                            price, priceThresholdEurMwh);
                    sungrowService.disablePowerLimit();
                } else {
                    log.debug("Feed-in limit already disabled — no change needed");
                }
            }
        } catch (SungrowCommunicationException e) {
            log.warn("Sungrow communication failed during feed-in limit adjustment: {}", e.getMessage());
        }
    }

    private boolean isEnabled() {
        return Boolean.parseBoolean(
                configService.getConfigValue("sungrow.feed_in_limit.enabled", "false")
        );
    }
}
