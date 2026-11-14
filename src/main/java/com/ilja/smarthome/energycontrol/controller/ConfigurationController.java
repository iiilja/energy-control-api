package com.ilja.smarthome.energycontrol.controller;

import com.ilja.smarthome.energycontrol.dto.ConfigEntryDto;
import com.ilja.smarthome.energycontrol.dto.ConfigValueRequest;
import com.ilja.smarthome.energycontrol.service.ConfigurationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/config")
@Tag(name = "Configuration", description = "Read and change system_configuration values")
@SecurityRequirement(name = "basicAuth")
public class ConfigurationController {

    private final ConfigurationService configService;

    public ConfigurationController(ConfigurationService configService) {
        this.configService = configService;
    }

    @GetMapping
    @Operation(summary = "All configuration values, sorted by key")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_ADMIN', 'ROLE_READONLY')")
    public List<ConfigEntryDto> getAll() {
        return configService.getAll();
    }

    @PutMapping("/{key:.+}")
    @Operation(summary = "Change an existing configuration value",
               description = "Known keys are checked (e.g. *.enabled must be true/false, "
                       + "heating.price_optimization.peak_threshold a number above 0): 400 if invalid, "
                       + "404 for an unknown key. Takes effect on the next run of the job that reads it; "
                       + "price optimisation settings re-plan at once.")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ConfigEntryDto update(@PathVariable String key, @Valid @RequestBody ConfigValueRequest request) {
        return configService.update(key, request.value());
    }
}
