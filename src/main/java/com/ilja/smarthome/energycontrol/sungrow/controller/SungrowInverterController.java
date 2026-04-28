package com.ilja.smarthome.energycontrol.sungrow.controller;

import com.ilja.smarthome.energycontrol.sungrow.dto.SungrowPowerLimitRequest;
import com.ilja.smarthome.energycontrol.sungrow.dto.SungrowStatusResponse;
import com.ilja.smarthome.energycontrol.sungrow.service.SungrowInverterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/sungrow")
@Tag(name = "Sungrow Inverter", description = "Sungrow SG15RT PV inverter control via Modbus TCP")
public class SungrowInverterController {

    private final SungrowInverterService inverterService;

    public SungrowInverterController(SungrowInverterService inverterService) {
        this.inverterService = inverterService;
    }

    @GetMapping("status")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_ADMIN', 'ROLE_READONLY')")
    @Operation(summary = "Read inverter status", description = "Returns running state and current power-limit settings")
    public SungrowStatusResponse getStatus() {
        return inverterService.getStatus();
    }

    @GetMapping("health")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_ADMIN', 'ROLE_READONLY')")
    @Operation(summary = "Check Modbus TCP connectivity")
    public ResponseEntity<Map<String, Object>> health() {
        boolean reachable = inverterService.testConnection();
        return ResponseEntity
                .status(reachable ? 200 : 503)
                .body(Map.of("reachable", reachable));
    }

    @PostMapping("power-limit")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_ADMIN')")
    @Operation(summary = "Set feed-in power limit",
               description = "Provide 'kw' (takes precedence) or 'percent'. Also enables the limit automatically.")
    public ResponseEntity<Void> setPowerLimit(@Valid @RequestBody SungrowPowerLimitRequest request) {
        if (request.hasKw()) {
            inverterService.setPowerLimitKw(request.kw());
        } else if (request.percent() != null) {
            inverterService.setPowerLimitPercent(request.percent());
        } else {
            return ResponseEntity.badRequest().build();
        }
        inverterService.enablePowerLimit();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("power-limit/enable")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_ADMIN')")
    @Operation(summary = "Enable feed-in power limit")
    public ResponseEntity<Void> enablePowerLimit() {
        inverterService.enablePowerLimit();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("power-limit/disable")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_ADMIN')")
    @Operation(summary = "Disable feed-in power limit (full output)")
    public ResponseEntity<Void> disablePowerLimit() {
        inverterService.disablePowerLimit();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("start")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(summary = "Send Run command to inverter")
    public ResponseEntity<Void> start() {
        inverterService.startInverter();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("stop")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(summary = "Send Stop command to inverter")
    public ResponseEntity<Void> stop() {
        inverterService.stopInverter();
        return ResponseEntity.noContent().build();
    }
}
