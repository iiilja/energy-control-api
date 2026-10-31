package com.ilja.smarthome.energycontrol.controller;

import com.ilja.smarthome.energycontrol.dto.heating.ManualOverrideRequest;
import com.ilja.smarthome.energycontrol.dto.heating.ManualOverrideResponse;
import com.ilja.smarthome.energycontrol.service.HeatingControlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/heating/setpoint/override")
@Tag(name = "Heating Setpoint", description = "Manage heating setpoint schedules based on electricity prices")
@SecurityRequirement(name = "basicAuth")
public class HeatingOverrideController {

    private final HeatingControlService heatingControlService;

    public HeatingOverrideController(HeatingControlService heatingControlService) {
        this.heatingControlService = heatingControlService;
    }

    @GetMapping
    @Operation(summary = "Get the manual setpoint override in effect",
               description = "Returns 204 when the schedule is in control")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_ADMIN', 'ROLE_READONLY')")
    public ResponseEntity<ManualOverrideResponse> getOverride() {
        return heatingControlService.getActiveOverride()
                .map(o -> ResponseEntity.ok(ManualOverrideResponse.from(o)))
                .orElse(ResponseEntity.noContent().build());
    }

    @PostMapping
    @Operation(summary = "Set a manual setpoint override",
               description = "Keeps the given setpoint for `hours` (default: heating.setpoint.manual_override.hours), "
                       + "then returns to the schedule. Replaces any current override and is applied immediately.")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_ADMIN')")
    public ResponseEntity<ManualOverrideResponse> setOverride(@Valid @RequestBody ManualOverrideRequest request) {
        return ResponseEntity.ok(ManualOverrideResponse.from(
                heatingControlService.startOverride(request.setpoint(), request.hours())));
    }

    @DeleteMapping
    @Operation(summary = "Cancel the manual setpoint override",
               description = "Returns to the schedule immediately. 204 if an override was cancelled, 404 if there was none.")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_ADMIN')")
    public ResponseEntity<Void> cancelOverride() {
        return heatingControlService.cancelOverride()
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
