package com.ilja.smarthome.energycontrol.controller;

import com.ilja.smarthome.energycontrol.dto.heating.PricePlanResponse;
import com.ilja.smarthome.energycontrol.service.PriceOptimizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

@RestController
@RequestMapping("/api/v1/heating/price-plan")
@Tag(name = "Heating Setpoint", description = "Manage heating setpoint schedules based on electricity prices")
@SecurityRequirement(name = "basicAuth")
public class PricePlanController {

    private static final ZoneId EUROPE_TALLINN = ZoneId.of("Europe/Tallinn");

    private final PriceOptimizationService optimizationService;
    private final Clock clock;

    public PricePlanController(PriceOptimizationService optimizationService, Clock clock) {
        this.optimizationService = optimizationService;
        this.clock = clock;
    }

    @GetMapping
    @Operation(summary = "A day's prices with peaks, compensation slots and heating setpoints",
               description = "Median, peak threshold and the kind of every 15-minute slot (NORMAL, PEAK, "
                       + "COMPENSATION), with the template, optimized and scheduled setpoint. Shown also while "
                       + "the optimisation is off, so its settings can be tuned first.")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_ADMIN', 'ROLE_READONLY')")
    public PricePlanResponse getPricePlan(
            @Parameter(description = "Tallinn date (default: today)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return optimizationService.describe(date != null ? date : LocalDate.now(clock.withZone(EUROPE_TALLINN)));
    }
}
