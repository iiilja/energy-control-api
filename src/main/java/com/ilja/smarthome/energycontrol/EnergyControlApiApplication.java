package com.ilja.smarthome.energycontrol;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for Energy Control API.
 *
 * This Spring Boot application controls the Thermia heat pump via Modbus TCP,
 * stores readings in PostgreSQL, and integrates Nordpool electricity prices.
 */
@SpringBootApplication
@EnableScheduling
public class EnergyControlApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(EnergyControlApiApplication.class, args);
    }
}
