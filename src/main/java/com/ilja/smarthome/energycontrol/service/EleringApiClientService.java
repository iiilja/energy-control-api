package com.ilja.smarthome.energycontrol.service;

import com.ilja.smarthome.energycontrol.dto.elering.EleringPriceResponse;
import com.ilja.smarthome.energycontrol.exception.EleringApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Service
@Slf4j
public class EleringApiClientService {

    private static final String ELERING_BASE_URL = "https://dashboard.elering.ee";
    private static final String PRICE_ENDPOINT = "/api/nps/price";
    private static final DateTimeFormatter API_DATE_FORMAT = DateTimeFormatter.ISO_INSTANT;

    private final RestClient restClient;

    @Autowired
    public EleringApiClientService(RestClient restClient) {
        this.restClient = restClient;
    }


    public EleringPriceResponse fetchNordpoolPrices(ZonedDateTime start, ZonedDateTime end) {
        String endpoint = ELERING_BASE_URL + PRICE_ENDPOINT;

        log.debug("Fetching Nordpool prices from Elering API: {}", endpoint);

        try {
            StringBuilder uriBuilder = new StringBuilder(endpoint);
            boolean hasParams = false;

            if (start != null) {
                uriBuilder.append("?start=").append(API_DATE_FORMAT.format(start.toInstant()));
                hasParams = true;
            }

            if (end != null) {
                uriBuilder.append(hasParams ? "&" : "?").append("end=").append(API_DATE_FORMAT.format(end.toInstant()));
            }

            String requestUri = uriBuilder.toString();
            log.debug("Requesting Elering API: {}", requestUri);

            EleringPriceResponse response = restClient.get()
                    .uri(requestUri)
                    .retrieve()
                    .body(EleringPriceResponse.class);

            if (response == null || !response.isSuccess()) {
                throw new EleringApiException("Invalid response from Elering API");
            }

            if (response.getData() == null || response.getData().isEmpty()) {
                throw new EleringApiException("No price data in Elering API response");
            }

            log.debug("Successfully fetched price data from Elering API");
            return response;

        } catch (ResourceAccessException e) {
            log.error("Connection error to Elering API at {}: {}", endpoint, e.getMessage());
            throw new EleringApiException(
                    "Cannot connect to Elering API. Is the service available?", e);

        } catch (RestClientException e) {
            log.error("HTTP error fetching Elering data: {}", e.getMessage());
            throw new EleringApiException(
                    "Failed to fetch data from Elering API: " + e.getMessage(), e);
        }
    }
}
