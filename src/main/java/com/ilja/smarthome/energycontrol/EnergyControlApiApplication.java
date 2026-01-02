package com.ilja.smarthome.energycontrol;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for Energy Control API.
 *
 * This Spring Boot application collects heat pump data from ESP32 devices,
 * stores it in PostgreSQL, and exposes it via OAuth2-secured REST APIs.
 */
@SpringBootApplication
@EnableScheduling
public class EnergyControlApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(EnergyControlApiApplication.class, args);
    }
}
