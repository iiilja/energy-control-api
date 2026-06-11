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
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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
            LocalDate today = LocalDate.now(EUROPE_TALLINN);
            ZonedDateTime start = today.atStartOfDay(EUROPE_TALLINN);
            ZonedDateTime end = start.plusDays(2);

            log.debug("Fetching prices for next day: {} to {}", start, end);
            EleringPriceResponse response = eleringClient.fetchNordpoolPrices(start, end);

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

    public List<NordpoolPrice> getPricesForDate(LocalDate date) {
        LocalDate targetDate = date != null ? date : LocalDate.now(EUROPE_TALLINN);
        ZonedDateTime start = targetDate.atStartOfDay(EUROPE_TALLINN);
        ZonedDateTime end = start.plusDays(1);
        return priceRepository.findByPriceTimestampBetweenOrderByPriceTimestampAsc(start, end);
    }

    public List<NordpoolPrice> getPricesForToday() {
        ZonedDateTime startOfDay = ZonedDateTime.now(EUROPE_TALLINN).toLocalDate().atStartOfDay(EUROPE_TALLINN);
        ZonedDateTime endOfDay = startOfDay.plusDays(1);
        return priceRepository.findByPriceTimestampBetweenOrderByPriceTimestampAsc(startOfDay, endOfDay);
    }

    public List<NordpoolPrice> getPricesForDateRange(ZonedDateTime start, ZonedDateTime end) {
        return priceRepository.findByPriceTimestampBetweenOrderByPriceTimestampAsc(start, end);
    }

    public List<NordpoolPrice> getHourlyAveragePricesForDate(LocalDate date) {
        LocalDate targetDate = date != null ? date : LocalDate.now(EUROPE_TALLINN);
        ZonedDateTime start = targetDate.atStartOfDay(EUROPE_TALLINN);
        ZonedDateTime end = start.plusDays(1);
        return getHourlyAveragePricesForDateRange(start, end);
    }

    public List<NordpoolPrice> getHourlyAveragePricesForDateRange(ZonedDateTime start, ZonedDateTime end) {
        return priceRepository.findByPriceTimestampBetweenOrderByPriceTimestampAsc(start, end)
                .stream()
                .collect(Collectors.groupingBy(
                        p -> p.getPriceTimestamp().truncatedTo(ChronoUnit.HOURS),
                        LinkedHashMap::new,
                        Collectors.averagingDouble(p -> p.getPrice().doubleValue())
                ))
                .entrySet().stream()
                .map(e -> new NordpoolPrice(e.getKey(), BigDecimal.valueOf(e.getValue()).setScale(4, RoundingMode.HALF_UP)))
                .collect(Collectors.toList());
    }

    public Optional<BigDecimal> getCurrentHourAveragePrice() {
        ZonedDateTime hourStart = ZonedDateTime.now(EUROPE_TALLINN).truncatedTo(ChronoUnit.HOURS);
        return priceRepository.findAveragePriceBetween(hourStart, hourStart.plusHours(1));
    }

    public boolean hasPricesForDate(LocalDate date) {
        ZonedDateTime start = date.atStartOfDay(EUROPE_TALLINN);
        ZonedDateTime end = start.plusDays(1);
        return priceRepository.countByPriceTimestampBetween(start, end) == 96;
    }

    @Transactional
    public void cleanupOldPrices(int daysToKeep) {
        ZonedDateTime cutoffDate = ZonedDateTime.now().minusDays(daysToKeep);
        priceRepository.deleteByPriceTimestampBefore(cutoffDate);
        log.info("Cleaned up Nordpool prices older than {} days", daysToKeep);
    }
}
