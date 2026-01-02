package com.ilja.smarthome.energycontrol.service;

import com.ilja.smarthome.energycontrol.domain.model.NordpoolPrice;
import com.ilja.smarthome.energycontrol.dto.elering.EleringPriceResponse;
import com.ilja.smarthome.energycontrol.exception.EleringApiException;
import com.ilja.smarthome.energycontrol.repository.NordpoolPriceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class NordpoolPriceService {

    private static final String ESTONIA_REGION = "ee";
    private static final ZoneId EUROPE_TALLINN = ZoneId.of("Europe/Tallinn");

    private final EleringApiClientService eleringClient;
    private final NordpoolPriceRepository priceRepository;

    @Autowired
    public NordpoolPriceService(
            EleringApiClientService eleringClient,
            NordpoolPriceRepository priceRepository) {
        this.eleringClient = eleringClient;
        this.priceRepository = priceRepository;
    }

    @Transactional
    public int fetchAndStorePrices() {
        log.info("Fetching Nordpool prices from Elering API");

        try {
            EleringPriceResponse response = eleringClient.fetchNordpoolPrices();

            List<EleringPriceResponse.PriceEntry> eeData = response.getData().get(ESTONIA_REGION);
            if (eeData == null || eeData.isEmpty()) {
                throw new EleringApiException("No Estonia (EE) price data in response");
            }

            List<NordpoolPrice> pricesToSave = new ArrayList<>();

            for (EleringPriceResponse.PriceEntry entry : eeData) {
                ZonedDateTime timestamp = Instant.ofEpochSecond(entry.getTimestamp())
                        .atZone(EUROPE_TALLINN);
                BigDecimal price = BigDecimal.valueOf(entry.getPrice());

                Optional<NordpoolPrice> existing = priceRepository.findByPriceTimestamp(timestamp);
                if (existing.isEmpty()) {
                    pricesToSave.add(new NordpoolPrice(timestamp, price));
                }
            }

            if (!pricesToSave.isEmpty()) {
                priceRepository.saveAll(pricesToSave);
                log.info("Stored {} new Nordpool price entries", pricesToSave.size());
            } else {
                log.info("No new price data to store (all entries already exist)");
            }

            return pricesToSave.size();

        } catch (EleringApiException e) {
            log.error("Failed to fetch and store Nordpool prices: {}", e.getMessage());
            throw e;
        }
    }

    public Optional<NordpoolPrice> getCurrentPrice() {
        return priceRepository.findPriceAtTimestamp(ZonedDateTime.now());
    }

    public Optional<NordpoolPrice> getPriceAt(ZonedDateTime timestamp) {
        return priceRepository.findPriceAtTimestamp(timestamp);
    }

    public List<NordpoolPrice> getPricesForToday() {
        ZonedDateTime startOfDay = ZonedDateTime.now(EUROPE_TALLINN).toLocalDate().atStartOfDay(EUROPE_TALLINN);
        ZonedDateTime endOfDay = startOfDay.plusDays(1);
        return priceRepository.findByPriceTimestampBetweenOrderByPriceTimestampAsc(startOfDay, endOfDay);
    }

    public List<NordpoolPrice> getPricesForDateRange(ZonedDateTime start, ZonedDateTime end) {
        return priceRepository.findByPriceTimestampBetweenOrderByPriceTimestampAsc(start, end);
    }

    @Transactional
    public void cleanupOldPrices(int daysToKeep) {
        ZonedDateTime cutoffDate = ZonedDateTime.now().minusDays(daysToKeep);
        priceRepository.deleteByPriceTimestampBefore(cutoffDate);
        log.info("Cleaned up Nordpool prices older than {} days", daysToKeep);
    }
}
