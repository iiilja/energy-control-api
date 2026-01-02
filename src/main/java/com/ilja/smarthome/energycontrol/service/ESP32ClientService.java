package com.ilja.smarthome.energycontrol.service;

import com.ilja.smarthome.energycontrol.dto.esp32.HeatPumpDataResponse;
import com.ilja.smarthome.energycontrol.exception.ESP32CommunicationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
@Slf4j
public class ESP32ClientService {

    private final RestClient restClient;
    private final ConfigurationService configService;

    @Autowired
    public ESP32ClientService(RestClient restClient, ConfigurationService configService) {
        this.restClient = restClient;
        this.configService = configService;
    }

    public HeatPumpDataResponse fetchHeatPumpData() {
        String baseUrl = configService.getConfigValue("esp32.base_url");
        String endpoint = baseUrl + "/api/data";

        log.debug("Fetching heat pump data from ESP32: {}", endpoint);

        try {
            HeatPumpDataResponse response = restClient.get()
                    .uri(endpoint)
                    .retrieve()
                    .body(HeatPumpDataResponse.class);

            if (response == null) {
                throw new ESP32CommunicationException("Empty response from ESP32");
            }

            log.debug("Successfully fetched heat pump data from ESP32");
            return response;

        } catch (ResourceAccessException e) {
            log.error("Connection error to ESP32 at {}: {}", endpoint, e.getMessage());
            throw new ESP32CommunicationException(
                    "Cannot connect to ESP32 at " + endpoint + ". Is the device online?", e);

        } catch (RestClientException e) {
            log.error("HTTP error fetching ESP32 data: {}", e.getMessage());
            throw new ESP32CommunicationException(
                    "Failed to fetch data from ESP32: " + e.getMessage(), e);
        }
    }

    /**
     * Test connectivity to the ESP32 device.
     *
     * @return true if connection successful, false otherwise
     */
    public boolean testConnection() {
        try {
            fetchHeatPumpData();
            log.info("ESP32 connection test successful");
            return true;
        } catch (ESP32CommunicationException e) {
            log.warn("ESP32 connection test failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get the current ESP32 base URL from configuration.
     *
     * @return ESP32 base URL
     */
    public String getEsp32BaseUrl() {
        return configService.getConfigValue("esp32.base_url");
    }

    public void setHeatingSetpoint(double temperature) {
        String baseUrl = configService.getConfigValue("esp32.base_url");
        String endpoint = baseUrl + "/api/setpoint";

        log.debug("Setting heating setpoint to {}°C on ESP32: {}", temperature, endpoint);

//        try {
//            SetpointRequest request = new SetpointRequest(temperature);
//
//            restClient.post()
//                    .uri(endpoint)
//                    .body(request)
//                    .retrieve()
//                    .body(String.class);
//
//            log.info("Successfully set heating setpoint to {}°C on ESP32", temperature);
//
//        } catch (ResourceAccessException e) {
//            log.error("Connection error to ESP32 at {}: {}", endpoint, e.getMessage());
//            throw new ESP32CommunicationException(
//                    "Cannot connect to ESP32 at " + endpoint + ". Is the device online?", e);
//
//        } catch (RestClientException e) {
//            log.error("HTTP error setting ESP32 setpoint: {}", e.getMessage());
//            throw new ESP32CommunicationException(
//                    "Failed to set setpoint on ESP32: " + e.getMessage(), e);
//        }
    }
}
