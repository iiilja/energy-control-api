package com.ilja.smarthome.energycontrol.controller;

import com.ilja.smarthome.energycontrol.service.NordpoolPriceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
