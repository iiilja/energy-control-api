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
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/heating/setpoint")
@Tag(name = "Heating Setpoint", description = "Manage heating setpoint schedules based on electricity prices")
@SecurityRequirement(name = "basicAuth")
@Slf4j
@Validated
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
               description = "Deletes all existing weekly schedule entries and replaces them with the provided list. "
                       + "The list must not be empty; duplicate day + start time is rejected with 400.")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_ADMIN')")
    public ResponseEntity<Void> saveWeeklySchedule(@RequestBody @NotEmpty List<@NotNull @Valid WeeklyScheduleEntryRequest> entries) {
        log.info("Replacing weekly schedule with {} entries", entries.size());
        heatingSetpointService.saveWeeklySchedule(entries);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/schedule")
    @Operation(summary = "Get heating setpoint schedule for a specific date",
               description = "Returns every Nordpool price slot of the day with its scheduled setpoint: the slot override if any, otherwise the weekly template")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_ADMIN', 'ROLE_READONLY')")
    public ResponseEntity<List<SetpointScheduleItemResponse>> getSchedule(
            @Parameter(description = "Date to fetch schedule for (YYYY-MM-DD)", example = "2025-12-31")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        log.debug("Fetching heating setpoint schedule for date: {}", date);
        List<SetpointScheduleItemResponse> schedule = heatingSetpointService.getScheduleForDate(date);
        return ResponseEntity.ok(schedule);
    }

    @PostMapping("/schedule")
    @Operation(summary = "Save setpoint overrides for single price slots",
               description = "Overrides the weekly template for specific 15-minute Nordpool price slots")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_ADMIN')")
    public ResponseEntity<Void> saveSchedule(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "List of setpoint schedules to save",
                    required = true)
            @RequestBody List<@NotNull @Valid SetpointScheduleItemRequest> schedules) {
        log.info("Saving {} heating setpoint schedules", schedules.size());
        heatingSetpointService.saveSchedules(schedules);
        return ResponseEntity.ok().build();
    }
}
