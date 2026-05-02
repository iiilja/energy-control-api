package com.ilja.smarthome.energycontrol.thermia.controller;

import com.ilja.smarthome.energycontrol.thermia.dto.HeatPumpDataResponse;
import com.ilja.smarthome.energycontrol.thermia.dto.ThermiaTapWaterTemperaturesRequest;
import com.ilja.smarthome.energycontrol.thermia.dto.ThermiaHeatCurveRequest;
import com.ilja.smarthome.energycontrol.thermia.dto.ThermiaOperationModeRequest;
import com.ilja.smarthome.energycontrol.thermia.dto.ThermiaSetpointRequest;
import com.ilja.smarthome.energycontrol.thermia.dto.ThermiaStatusResponse;
import com.ilja.smarthome.energycontrol.thermia.dto.ThermiaTemperaturesResponse;
import com.ilja.smarthome.energycontrol.thermia.service.ThermiaHeatPumpService;
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
@RequestMapping("/api/v1/thermia")
@Tag(name = "Thermia Heat Pump", description = "Thermia heat pump control via Modbus TCP")
public class ThermiaHeatPumpController {

    private final ThermiaHeatPumpService heatPumpService;

    public ThermiaHeatPumpController(ThermiaHeatPumpService heatPumpService) {
        this.heatPumpService = heatPumpService;
    }

    @GetMapping("data")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_ADMIN', 'ROLE_READONLY')")
    @Operation(summary = "Full heat pump data snapshot (ESP32 /api/data compatible format)")
    public HeatPumpDataResponse getData() {
        return heatPumpService.getHeatPumpData();
    }

    @GetMapping("status")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_ADMIN', 'ROLE_READONLY')")
    @Operation(summary = "Read full heat pump status",
               description = "Returns temperatures, operation mode, compressor data, alarms, heat curve, and enable states")
    public ThermiaStatusResponse getStatus() {
        return heatPumpService.getStatus();
    }

    @GetMapping("temperatures")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_ADMIN', 'ROLE_READONLY')")
    @Operation(summary = "Read temperature sensors only")
    public ThermiaTemperaturesResponse getTemperatures() {
        return heatPumpService.getTemperatures();
    }

    @GetMapping("health")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_ADMIN', 'ROLE_READONLY')")
    @Operation(summary = "Check Modbus TCP connectivity to heat pump")
    public ResponseEntity<Map<String, Object>> health() {
        boolean reachable = heatPumpService.testConnection();
        return ResponseEntity
                .status(reachable ? 200 : 503)
                .body(Map.of("reachable", reachable));
    }

    @PostMapping("mode")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_ADMIN')")
    @Operation(summary = "Set operation mode", description = "1=OFF, 2=Standby, 3=ON/Auto")
    public ResponseEntity<Void> setMode(@Valid @RequestBody ThermiaOperationModeRequest request) {
        heatPumpService.setOperationMode(request.mode());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("setpoint")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_ADMIN')")
    @Operation(summary = "Set comfort wheel temperature (desired indoor temp), 15–30 °C")
    public ResponseEntity<Void> setSetpoint(@Valid @RequestBody ThermiaSetpointRequest request) {
        heatPumpService.setComfortSetpoint(request.temperature());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("tap-water-temperatures")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_ADMIN')")
    @Operation(summary = "Set tap water start/stop temperatures")
    public ResponseEntity<Void> setTapWaterTemperatures(@Valid @RequestBody ThermiaTapWaterTemperaturesRequest request) {
        heatPumpService.setTapWaterTemperatures(request.startTemperature(), request.stopTemperature());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("heat-curve")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(summary = "Update heat curve Y-axis (supply temperature) points",
               description = "Exactly 7 values in °C (15–65), corresponding to the 7 outdoor temperature points")
    public ResponseEntity<Void> setHeatCurve(@Valid @RequestBody ThermiaHeatCurveRequest request) {
        heatPumpService.setHeatCurveY(request.points().stream().mapToDouble(Double::doubleValue).toArray());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("heating/enable")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_ADMIN')")
    @Operation(summary = "Enable space heating")
    public ResponseEntity<Void> enableHeating() {
        heatPumpService.enableHeating();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("heating/disable")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_ADMIN')")
    @Operation(summary = "Disable space heating")
    public ResponseEntity<Void> disableHeating() {
        heatPumpService.disableHeating();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("tap-water/enable")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_ADMIN')")
    @Operation(summary = "Enable tap water production")
    public ResponseEntity<Void> enableTapWater() {
        heatPumpService.enableTapWater();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("tap-water/disable")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_ADMIN')")
    @Operation(summary = "Disable tap water production")
    public ResponseEntity<Void> disableTapWater() {
        heatPumpService.disableTapWater();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("cooling/enable")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(summary = "Enable active cooling")
    public ResponseEntity<Void> enableCooling() {
        heatPumpService.enableCooling();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("cooling/disable")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(summary = "Disable active cooling")
    public ResponseEntity<Void> disableCooling() {
        heatPumpService.disableCooling();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("hot-gas-pump/enable")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(summary = "Enable hot gas pump (coil 14 / de-facto 15)")
    public ResponseEntity<Void> enableHotGasPump() {
        heatPumpService.enableHotGasPump();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("hot-gas-pump/disable")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(summary = "Disable hot gas pump (coil 14 / de-facto 15)")
    public ResponseEntity<Void> disableHotGasPump() {
        heatPumpService.disableHotGasPump();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("anti-legionella/enable")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(summary = "Manually trigger anti-legionella cycle (coil 24 / de-facto 25)")
    public ResponseEntity<Void> enableAntiLegionella() {
        heatPumpService.enableAntiLegionella();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("anti-legionella/disable")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(summary = "Stop anti-legionella cycle (coil 24 / de-facto 25)")
    public ResponseEntity<Void> disableAntiLegionella() {
        heatPumpService.disableAntiLegionella();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("internal-heater/enable")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_ADMIN')")
    @Operation(summary = "Enable internal additional heater (coil 4 / de-facto 5)")
    public ResponseEntity<Void> enableInternalAdditionalHeater() {
        heatPumpService.enableInternalAdditionalHeater();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("internal-heater/disable")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_ADMIN')")
    @Operation(summary = "Disable internal additional heater (coil 4 / de-facto 5)")
    public ResponseEntity<Void> disableInternalAdditionalHeater() {
        heatPumpService.disableInternalAdditionalHeater();
        return ResponseEntity.noContent().build();
    }
}
