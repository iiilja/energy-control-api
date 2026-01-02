package com.ilja.smarthome.energycontrol.controller;

import com.ilja.smarthome.energycontrol.dto.heating.SetpointScheduleItemRequest;
import com.ilja.smarthome.energycontrol.dto.heating.SetpointScheduleItemResponse;
import com.ilja.smarthome.energycontrol.service.HeatingSetpointService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/heating/setpoint")
@Slf4j
public class HeatingSetpointController {

    private final HeatingSetpointService heatingSetpointService;

    @Autowired
    public HeatingSetpointController(HeatingSetpointService heatingSetpointService) {
        this.heatingSetpointService = heatingSetpointService;
    }

    @GetMapping("/schedule")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_ADMIN', 'ROLE_READONLY')")
    public ResponseEntity<List<SetpointScheduleItemResponse>> getSchedule(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        log.debug("Fetching heating setpoint schedule for date: {}", date);
        List<SetpointScheduleItemResponse> schedule = heatingSetpointService.getScheduleForDate(date);
        return ResponseEntity.ok(schedule);
    }

    @PostMapping("/schedule")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_ADMIN')")
    public ResponseEntity<Void> saveSchedule(@RequestBody List<SetpointScheduleItemRequest> schedules) {
        log.info("Saving {} heating setpoint schedules", schedules.size());
        heatingSetpointService.saveSchedules(schedules);
        return ResponseEntity.ok().build();
    }
}
