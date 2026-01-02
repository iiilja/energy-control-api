package com.ilja.smarthome.energycontrol.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Configuration for RestClient used to communicate with ESP32 device.
 */
@Configuration
public class RestClientConfig {

    /**
     * Create a RestClient bean for making HTTP requests.
     */
    @Bean
    public RestClient restClient() {
        return RestClient.builder()
                .requestFactory(clientHttpRequestFactory())
                .build();
    }

    /**
     * Configure HTTP request factory with timeout settings.
     */
    private ClientHttpRequestFactory clientHttpRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000); // 10 seconds
        factory.setReadTimeout(10000);    // 10 seconds
        return factory;
    }
}
