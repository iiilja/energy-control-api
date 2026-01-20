package com.ilja.smarthome.energycontrol.controller;

import com.ilja.smarthome.energycontrol.service.ConfigurationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

/**
 * Proxy controller that forwards /modbus-api requests to the ESP32 Modbus API.
 */
@RestController
@RequestMapping("/modbus-api")
@Slf4j
public class ModbusApiProxyController {

    private final ConfigurationService configService;
    private final RestClient restClient;

    @Autowired
    public ModbusApiProxyController(ConfigurationService configService, RestClient restClient) {
        this.configService = configService;
        this.restClient = restClient;
    }

    @GetMapping("/**")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_ADMIN', 'ROLE_READONLY')")
    public ResponseEntity<String> proxyGet(HttpServletRequest request) {
        return proxyRequest(request, null);
    }

    @PostMapping("/**")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_ADMIN')")
    public ResponseEntity<String> proxyPost(HttpServletRequest request, @RequestBody(required = false) String body) {
        return proxyRequest(request, body);
    }

    @PutMapping("/**")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_ADMIN')")
    public ResponseEntity<String> proxyPut(HttpServletRequest request, @RequestBody(required = false) String body) {
        return proxyRequest(request, body);
    }

    @DeleteMapping("/**")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<String> proxyDelete(HttpServletRequest request) {
        return proxyRequest(request, null);
    }

    private ResponseEntity<String> proxyRequest(HttpServletRequest request, String body) {
        String esp32BaseUrl = configService.getConfigValue("esp32.base_url");

        // Extract the path after /modbus-api
        String path = request.getRequestURI().substring("/modbus-api".length());
        String targetUrl = esp32BaseUrl + "/api" + path;

        // Add query parameters if present
        if (request.getQueryString() != null) {
            targetUrl += "?" + request.getQueryString();
        }

        log.debug("Proxying {} request to ESP32: {}", request.getMethod(), targetUrl);

        try {
            RestClient.RequestBodySpec requestSpec = restClient.method(
                    org.springframework.http.HttpMethod.valueOf(request.getMethod())
            ).uri(targetUrl);

            if (body != null && !body.isEmpty()) {
                requestSpec.body(body);
            }

            String response = requestSpec.retrieve().body(String.class);

            log.debug("Successfully proxied request to ESP32");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error proxying request to ESP32: {}", e.getMessage());
            return ResponseEntity.status(502)
                    .body("{\"error\": \"Error communicating with ESP32: " + e.getMessage() + "\"}");
        }
    }
}
