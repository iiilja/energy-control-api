package com.ilja.smarthome.energycontrol.controller;

import com.ilja.smarthome.energycontrol.dto.heating.SetpointScheduleItemRequest;
import com.ilja.smarthome.energycontrol.dto.heating.SetpointScheduleItemResponse;
import com.ilja.smarthome.energycontrol.dto.heating.WeeklyScheduleEntryDto;
import com.ilja.smarthome.energycontrol.dto.heating.WeeklyScheduleEntryRequest;
import com.ilja.smarthome.energycontrol.service.HeatingSetpointService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
@RequestMapping("/api/v1/heating/setpoint")
@Tag(name = "Heating Setpoint", description = "Manage heating setpoint schedules based on electricity prices")
@SecurityRequirement(name = "basicAuth")
@Slf4j
public class HeatingSetpointController {

    private final HeatingSetpointService heatingSetpointService;

    @Autowired
    public HeatingSetpointController(HeatingSetpointService heatingSetpointService) {
        this.heatingSetpointService = heatingSetpointService;
    }

    @GetMapping("/weekly-schedule")
    @Operation(summary = "Get default weekly heating schedule template")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_ADMIN', 'ROLE_READONLY')")
    public ResponseEntity<List<WeeklyScheduleEntryDto>> getWeeklySchedule() {
        return ResponseEntity.ok(heatingSetpointService.getWeeklySchedule());
    }

    @PutMapping("/weekly-schedule")
    @Operation(summary = "Replace default weekly heating schedule template",
               description = "Deletes all existing weekly schedule entries and replaces them with the provided list")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_ADMIN')")
    public ResponseEntity<Void> saveWeeklySchedule(@RequestBody List<WeeklyScheduleEntryRequest> entries) {
        log.info("Replacing weekly schedule with {} entries", entries.size());
        heatingSetpointService.saveWeeklySchedule(entries);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/schedule")
    @Operation(summary = "Get heating setpoint schedule for a specific date",
               description = "Returns all Nordpool price entries with configured setpoints (or default 21°C if not configured)")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_ADMIN', 'ROLE_READONLY')")
    public ResponseEntity<List<SetpointScheduleItemResponse>> getSchedule(
            @Parameter(description = "Date to fetch schedule for (YYYY-MM-DD)", example = "2025-12-31")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        log.debug("Fetching heating setpoint schedule for date: {}", date);
        List<SetpointScheduleItemResponse> schedule = heatingSetpointService.getScheduleForDate(date);
        return ResponseEntity.ok(schedule);
    }

    @PostMapping("/schedule")
    @Operation(summary = "Save heating setpoint schedules",
               description = "Saves or updates heating setpoint schedules for specific Nordpool price entries")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_ADMIN')")
    public ResponseEntity<Void> saveSchedule(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "List of setpoint schedules to save",
                    required = true)
            @RequestBody List<SetpointScheduleItemRequest> schedules) {
        log.info("Saving {} heating setpoint schedules", schedules.size());
        heatingSetpointService.saveSchedules(schedules);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/schedule/apply-template")
    @Operation(summary = "Apply weekly template to a specific date",
               description = "Applies the default weekly schedule template to all Nordpool price entries for the specified date")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_ADMIN')")
    public ResponseEntity<Map<String, Object>> applyWeeklyTemplate(
            @Parameter(description = "Date to apply template to (YYYY-MM-DD)", example = "2025-12-31")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        log.info("Applying weekly template to date: {}", date);
        int schedulesCreated = heatingSetpointService.applyWeeklyTemplateToDate(date);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "date", date,
                "schedulesCreated", schedulesCreated
        ));
    }
}
