package com.ilja.smarthome.energycontrol.controller;

import com.ilja.smarthome.energycontrol.dto.nordpool.CurrentNordpoolPriceDto;
import com.ilja.smarthome.energycontrol.dto.nordpool.NordpoolPriceDto;
import com.ilja.smarthome.energycontrol.service.NordpoolPriceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/nordpool")
@Tag(name = "Nordpool Prices", description = "Fetch and manage Nordpool electricity prices")
@SecurityRequirement(name = "basicAuth")
@Slf4j
public class NordpoolPriceController {

    private final NordpoolPriceService nordpoolPriceService;

    @Autowired
    public NordpoolPriceController(NordpoolPriceService nordpoolPriceService) {
        this.nordpoolPriceService = nordpoolPriceService;
    }

    @GetMapping("/prices")
    @Operation(summary = "Get Nordpool prices for a date",
               description = "Returns stored Nordpool prices for the given date (defaults to today, Europe/Tallinn timezone)")
    public ResponseEntity<List<NordpoolPriceDto>> getPrices(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        List<NordpoolPriceDto> result = nordpoolPriceService.getPricesForDate(date)
                .stream()
                .map(p -> new NordpoolPriceDto(p.getPriceTimestamp().toOffsetDateTime().toString(), p.getPrice()))
                .toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/prices/hourly-average")
    @Operation(summary = "Get hourly average Nordpool prices for a date range",
               description = "Returns Nordpool prices averaged per hour for the given date range (defaults to today, Europe/Tallinn timezone)")
    public ResponseEntity<List<NordpoolPriceDto>> getHourlyAveragePrices(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        List<NordpoolPriceDto> result = nordpoolPriceService.getHourlyAveragePricesForDate(date)
                .stream()
                .map(p -> new NordpoolPriceDto(p.getPriceTimestamp().toOffsetDateTime().toString(), p.getPrice()))
                .toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/current-price")
    @Operation(summary = "Get current Nordpool price",
               description = "Returns the current 15-min slot price and the hourly average for the current hour (Europe/Tallinn timezone)")
    public ResponseEntity<CurrentNordpoolPriceDto> getCurrentPrice() {
        return nordpoolPriceService.getCurrentPrice()
                .map(p -> {
                    var hourlyAvg = nordpoolPriceService.getCurrentHourAveragePrice().orElse(null);
                    return ResponseEntity.ok(new CurrentNordpoolPriceDto(
                            p.getPriceTimestamp().toOffsetDateTime().toString(),
                            p.getPrice(),
                            hourlyAvg));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/fetch")
    @Operation(summary = "Manually trigger Nordpool price fetch",
               description = "Fetches current Nordpool prices from Elering API and stores them in the database. Normally runs automatically at 15:00 daily.")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<Map<String, Object>> triggerManualFetch() {
        log.info("Manual Nordpool price fetch triggered via API");
        int pricesStored = nordpoolPriceService.fetchAndStorePrices();

        return ResponseEntity.ok(Map.of(
                "success", true,
                "pricesStored", pricesStored,
                "message", String.format("Successfully fetched and stored %d price entries", pricesStored)
        ));
    }
}
